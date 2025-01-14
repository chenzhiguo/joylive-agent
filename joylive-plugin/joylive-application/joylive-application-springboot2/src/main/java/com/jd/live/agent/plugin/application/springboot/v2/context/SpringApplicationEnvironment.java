/*
 * Copyright © ${year} ${owner} (${email})
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.live.agent.plugin.application.springboot.v2.context;

import com.jd.live.agent.core.bootstrap.ApplicationEnvironment;
import com.jd.live.agent.core.bootstrap.ApplicationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.NonNull;

public class SpringApplicationEnvironment implements ApplicationEnvironment {

    private final ConfigurableEnvironment environment;

    public SpringApplicationEnvironment(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void addFirst(ApplicationPropertySource propertySource) {
        environment.getPropertySources().addFirst(new ConfiguratorSource(propertySource));
    }

    @Override
    public void addLast(ApplicationPropertySource propertySource) {
        environment.getPropertySources().addLast(new ConfiguratorSource(propertySource));
    }

    /**
     * A PropertySource implementation that wraps a Configurator instance and provides access to its
     * configuration properties.
     */
    private static class ConfiguratorSource extends PropertySource<ApplicationPropertySource> {

        ConfiguratorSource(ApplicationPropertySource configurator) {
            super(configurator.getName(), configurator);
        }

        @Override
        public Object getProperty(@NonNull String name) {
            return source.getProperty(name);
        }
    }
}
