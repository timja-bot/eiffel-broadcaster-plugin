/**
 The MIT License

 Copyright 2022 Axis Communications AB.

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

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import org.jenkinsci.Symbol;
import org.junit.Rule;
import org.junit.Test;

import static io.jenkins.plugins.casc.misc.Util.getUnclassifiedRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ConfigurationAsCodeTest {
    // {@link JenkinsConfiguredWithCodeRule} requires that the formal type of the rule attribute
    // is JenkinsConfiguredWithCodeRule.
    @Rule
    public JenkinsConfiguredWithCodeRule jenkins = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("jcasc-input.yml")
    public void testSupportsConfigurationAsCode() throws Exception {
        EiffelBroadcasterConfig config = EiffelBroadcasterConfig.getInstance();
        assertThat(config.getAppId(), is("random-appid"));
        assertThat(config.getEnableBroadcaster(), is(true));
        assertThat(config.getExchangeName(), is("eiffel-exchange"));
        assertThat(config.getHostnameSource(), is(HostnameSource.CONFIGURED_URL));
        assertThat(config.getPersistentDelivery(), is(false));
        assertThat(config.getRoutingKey(), is("random-routing-key"));
        assertThat(config.getServerUri(), is("amqp://rabbitmq.example.com"));
        assertThat(config.getUserName(), is("johndoe"));
        assertThat(config.getVirtualHost(), is("/"));
    }

    @Test
    @ConfiguredWithCode("jcasc-input.yml")
    public void testSupportsConfigurationExport() throws Exception {
        EiffelBroadcasterConfig config = EiffelBroadcasterConfig.getInstance();
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        String pluginShortName = EiffelBroadcasterConfig.class.getAnnotation(Symbol.class).value()[0];
        CNode pluginNode = getUnclassifiedRoot(context).get(pluginShortName);
        String sanitizedYAML = toYamlString(pluginNode).replaceFirst("(?m)^userPassword: .*(?:\\r?\\n)?", "");
        assertThat(sanitizedYAML, is(toStringFromYamlFile(this, "jcasc-expected-output.yml")));
    }
}
