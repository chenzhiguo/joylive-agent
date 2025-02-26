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
package com.jd.live.agent.plugin.application.springboot.v2.config;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * A factory class that provides instances of Spring configuration refresher.
 */
public class SpringRefresherFactory {

    /**
     * The type of event that is published when the environment changes.
     */
    private static final String TYPE_ENVIRONMENT_CHANGE_EVENT = "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    /**
     * Gets the refresher instance based on the given Spring application context.
     *
     * @param context The Spring application context.
     * @return The refresher instance.
     */
    public static ConfigRefresher getRefresher(ConfigurableApplicationContext context) {
        // avoid none EnvironmentChangeEvent class.
        try {
            ClassLoader classLoader = context.getClass().getClassLoader();
            classLoader.loadClass(TYPE_ENVIRONMENT_CHANGE_EVENT);
            return new SpringCloudRefresher(context);
        } catch (Throwable e) {
            return new SpringBootRefresher();
        }
    }
}
