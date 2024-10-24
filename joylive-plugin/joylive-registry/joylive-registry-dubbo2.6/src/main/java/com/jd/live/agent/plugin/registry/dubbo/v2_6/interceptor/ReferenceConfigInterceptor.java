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
package com.jd.live.agent.plugin.registry.dubbo.v2_6.interceptor;

import com.alibaba.dubbo.config.ReferenceConfig;
import com.jd.live.agent.bootstrap.bytekit.context.ExecutableContext;
import com.jd.live.agent.core.instance.Application;
import com.jd.live.agent.core.plugin.definition.InterceptorAdaptor;
import com.jd.live.agent.governance.policy.PolicySupplier;

import java.util.Map;

/**
 * ReferenceConfigInterceptor
 */
public class ReferenceConfigInterceptor extends InterceptorAdaptor {

    private final Application application;

    private final PolicySupplier policySupplier;

    public ReferenceConfigInterceptor(Application application, PolicySupplier policySupplier) {
        this.application = application;
        this.policySupplier = policySupplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEnter(ExecutableContext ctx) {
        Map<String, String> argument = (Map<String, String>) ctx.getArguments()[0];
        ReferenceConfig<?> config = (ReferenceConfig<?>) ctx.getTarget();

        application.labelRegistry(argument::putIfAbsent);
        policySupplier.subscribe(config.getInterface());
    }

}
