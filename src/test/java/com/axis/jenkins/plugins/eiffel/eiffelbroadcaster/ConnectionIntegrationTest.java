/**
 The MIT License

 Copyright 2020 Axis Communications AB.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package com.axis.jenkins.plugins.eiffel.eiffelbroadcaster;

import com.axis.jenkins.plugins.eiffel.eiffelbroadcaster.eiffel.EiffelEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.ToxiproxyContainer;

import static org.junit.Assert.*;
import static org.testcontainers.containers.Network.newNetwork;

/**
 * Integration tests for the MQConnection
 *
 * @author Hampus Johansson
 */
public class ConnectionIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionIntegrationTest.class);
    private static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";
    private static final int DEFAULT_MESSAGE_WAIT = 10;

    RabbitMQContainer defaultMQContainer = TestUtil.getDefaultMQContainer();
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Rule
    public Network network = newNetwork();

    private ToxiproxyContainer toxiproxy = new ToxiproxyContainer()
            .withNetwork(network)
            .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);

    @Rule
    public TestRule chain = RuleChain
            .outerRule(defaultMQContainer.withNetwork(network))
            .around(toxiproxy)
            .around(new JenkinsRule());

    /**
     * Get the toxiproxy for the MQ container
     */
    private ToxiproxyContainer.ContainerProxy getProxy() {
        return toxiproxy.getProxy(defaultMQContainer, TestUtil.PORT);
    }

    /**
     * Creates a connection to RabbitMQ through toxiproxy and configures exchanges, passwords etc.
     */
    @Before
    public void createProxyConnection() {
        EiffelBroadcasterConfig config = EiffelBroadcasterConfig.getInstance();
        assertNotNull("No config available: EiffelBroadcasterConfig", config);
        TestUtil.setDefaultConfig(config);
        config.setServerUri(formatProxyServerUri());

        MQConnection conn = MQConnection.getInstance();
        conn.initialize(config.getUserName(), config.getUserPassword(), config.getServerUri(), config.getVirtualHost());
    }

    /**
     * Format a URI to toxiproxy, which will be forwarded to RabbitMQ.
     */
    private String formatProxyServerUri() {
        ToxiproxyContainer.ContainerProxy proxy = getProxy();
        final String ipAddressViaToxiproxy = proxy.getContainerIpAddress();
        final int portViaToxiproxy = proxy.getProxyPort();
        return "amqp://" + ipAddressViaToxiproxy + ":" + portViaToxiproxy;
    }

    /**
     * Publishes a single event and silently ignores any errors. Use this advisedly
     * and only when a later stage of the test will reveal that the sending failed.
     *
     * @param event the event to publish
     */
    private void publishSilently(final EiffelEvent event) {
        try {
            Util.mustPublishEvent(event);
        } catch (Exception e) {
            LOGGER.error("Unexpected exception", e);
        }
    }

    /**
     * Test that the publisher sends messages correctly.
     */
    @Test
    public void testSentMessagesHaveCorrectFormat() throws InterruptedException, IOException {
        MQConnection conn = MQConnection.getInstance();
        int messageCount = 10;
        ArrayList<EiffelEvent> expectedMessages = TestUtil.createEvents(messageCount);
        expectedMessages.forEach(this::publishSilently);
        ArrayList<EiffelEvent> actualMessages = TestUtil.waitForMessages(
                conn,
                messageCount,
                DEFAULT_MESSAGE_WAIT,
                TestUtil.QUEUE_NAME
        );
        assertEquals(expectedMessages, actualMessages);
    }

    /**
     * Test that the publisher won't lose messages when the connection is closed.
     */
    @Test
    public void testSendMessagesHandlesClosedConnection() throws InterruptedException, IOException {
        MQConnection conn = MQConnection.getInstance();
        int messageCount = 1000;
        ArrayList<EiffelEvent> expectedMessages = TestUtil.createEvents(messageCount);

        getProxy().setConnectionCut(true);
        executor.submit(() -> {
            expectedMessages.forEach(this::publishSilently);
        });
        Thread.sleep(1000);
        getProxy().setConnectionCut(false);
        ArrayList<EiffelEvent> actualMessages = TestUtil.waitForMessages(
                conn,
                messageCount,
                DEFAULT_MESSAGE_WAIT,
                TestUtil.QUEUE_NAME
        );
        assertEquals(expectedMessages, actualMessages);
    }

}
