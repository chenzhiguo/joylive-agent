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
package com.jd.live.agent.plugin.protection.mariadb.v2.interceptor;

import com.jd.live.agent.bootstrap.bytekit.context.ExecutableContext;
import com.jd.live.agent.bootstrap.bytekit.context.MethodContext;
import com.jd.live.agent.governance.interceptor.AbstractDbInterceptor;
import com.jd.live.agent.governance.policy.PolicySupplier;
import com.jd.live.agent.plugin.protection.mariadb.v2.request.MariadbRequest;
import org.mariadb.jdbc.internal.protocol.AbstractQueryProtocol;
import org.mariadb.jdbc.internal.protocol.Protocol;

/**
 * ExecutePrepareInterceptor
 */
public class ExecutePrepareInterceptor extends AbstractDbInterceptor {

    public ExecutePrepareInterceptor(PolicySupplier policySupplier) {
        super(policySupplier);
    }

    /**
     * Enhanced logic before method execution<br>
     * <p>
     *
     * @param ctx ExecutableContext
     * @see AbstractQueryProtocol#prepare(String, boolean)
     */
    @Override
    public void onEnter(ExecutableContext ctx) {
        Protocol protocol = (Protocol) ctx.getTarget();
        String sql = (String) ctx.getArguments()[0];
        protect((MethodContext) ctx, new MariadbRequest(protocol, sql));
    }

}
