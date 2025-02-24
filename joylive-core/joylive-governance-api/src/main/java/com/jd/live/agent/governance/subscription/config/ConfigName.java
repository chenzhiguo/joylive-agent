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
package com.jd.live.agent.governance.subscription.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigName {

    private String namespace;

    private String name;

    private String profile;

    private transient String fullName;

    public ConfigName() {
    }

    public ConfigName(String namespace, String name, String profile) {
        this.namespace = namespace;
        this.name = name;
        this.profile = profile;
    }

    public boolean validate() {
        return name != null && !name.isEmpty();
    }

    @Override
    public String toString() {
        if (fullName == null) {
            boolean withProfile = profile == null || profile.isEmpty();
            boolean withNamespace = namespace == null || namespace.isEmpty();
            String configName = name == null ? "" : name;
            if (withProfile && withNamespace) {
                fullName = configName + "@" + profile + "@" + namespace;
            } else if (withNamespace) {
                fullName = configName + "@@" + namespace;
            } else if (withProfile) {
                fullName = configName + "@" + profile;
            } else {
                fullName = configName;
            }
        }
        return fullName;
    }
}

