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
package com.jd.live.agent.core.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;

/**
 * PluginConfig
 *
 * @since 1.0.0
 */
@Setter
@Getter
public class PluginConfig {

    public static final String COMPONENT_PLUGIN_CONFIG = "pluginConfig";

    private Set<String> systems;

    private Set<String> statics;

    private Set<String> dynamics;

    private Set<String> disables;

    private Map<String, Set<String>> profiles;

    private String profile;

    public boolean isSystemActive(String name) {
        return isEnabled(name) && isSystem(name);
    }

    public boolean isStaticActive(String name) {
        return isEnabled(name) && isStatic(name) && isProfileActive(name);
    }

    public boolean isDynamicActive(String name) {
        return isEnabled(name) && isDynamic(name) && isProfileActive(name);
    }

    public boolean isProfileActive(String name) {
        if (profile == null || profile.isEmpty() || profiles == null) {
            return true;
        } else {
            Set<String> profileSet = profiles.get(profile);
            return profileSet != null && profileSet.contains(name);
        }
    }

    public boolean isSystem(String name) {
        return systems != null && systems.contains(name);
    }

    public boolean isStatic(String name) {
        return statics != null && statics.contains(name);
    }

    public boolean isEnabled(String name) {
        return disables == null || !disables.contains(name);
    }

    public boolean isDynamic(String name) {
        return dynamics != null && dynamics.contains(name);
    }

}
