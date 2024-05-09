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
package com.jd.live.agent.plugin.router.sofarpc.request;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.jd.live.agent.governance.request.AbstractRpcRequest.AbstractRpcInboundRequest;
import com.jd.live.agent.governance.request.AbstractRpcRequest.AbstractRpcOutboundRequest;
import com.jd.live.agent.governance.request.StickyRequest;

/**
 * SofaRpcRequest
 *
 * @since 1.0.0
 */
public interface SofaRpcRequest {

    /**
     * Represents an inbound RPC request for the SOFA framework, encapsulating the necessary
     * details for processing the request on the server side.
     * <p>
     * This class extends {@link AbstractRpcInboundRequest} with a specific focus on SOFA RPC requests,
     * providing a structure to handle incoming service method invocations. It captures essential
     * details such as the service interface name, method name, arguments, and any additional
     * properties attached with the request. This information facilitates the execution of the
     * corresponding service method on the server.
     * </p>
     *
     * @see AbstractRpcInboundRequest for the base class functionality.
     */
    class SofaRpcInboundRequest extends AbstractRpcInboundRequest<SofaRequest> implements SofaRpcRequest {

        /**
         * Constructs a new {@code SofaRpcInboundRequest} with the specified SOFA request details.
         * <p>
         * Initializes the request with comprehensive details about the service method to be executed,
         * including the service interface name, the unique name of the target service, the method name,
         * method arguments, and any additional properties (attachments) that may accompany the request.
         * This constructor parses the unique service name to extract the service group if specified.
         * </p>
         *
         * @param request the {@link SofaRequest} containing the details of the service method invocation.
         */
        public SofaRpcInboundRequest(SofaRequest request) {
            super(request);
            this.service = request.getInterfaceName();
            String uniqueName = request.getTargetServiceUniqueName();
            int pos = uniqueName.lastIndexOf(':');
            this.group = pos < 0 ? null : uniqueName.substring(pos + 1);
            this.method = request.getMethodName();
            this.arguments = request.getMethodArgs();
            this.attachments = request.getRequestProps();
        }
    }

    /**
     * Represents an outbound RPC request specifically designed for the SOFA framework.
     * <p>
     * This class encapsulates the details required to execute a remote procedure call using the SOFA framework,
     * including service identification, method invocation details, and any additional attachments that may be necessary
     * for the call. It extends the generic {@link AbstractRpcOutboundRequest} class, providing SOFA-specific
     * implementation details.
     * </p>
     *
     * @see AbstractRpcOutboundRequest for more information on the base class functionality.
     */
    class SofaRpcOutboundRequest extends AbstractRpcOutboundRequest<SofaRequest> implements SofaRpcRequest {

        private final StickyRequest stickyRequest;

        /**
         * Creates a new SofaRpcOutboundRequest without a sticky session identifier. This constructor is used
         * when sticky session routing is not required for the RPC call.
         * <p>
         * Initializes the request with the provided SOFA request details, extracting necessary information
         * such as service interface name, method name, arguments, and any attachments.
         * </p>
         *
         * @param request The SOFA request containing the RPC call details.
         */
        public SofaRpcOutboundRequest(SofaRequest request) {
            this(request, null);
        }

        /**
         * Creates a new SofaRpcOutboundRequest with the specified SOFA request details and an optional sticky session
         * identifier. This constructor supports scenarios where sticky session routing is desired, allowing subsequent
         * requests to be routed to the same provider.
         *
         * @param request  The SOFA request containing the RPC call details.
         * @param stickyRequest A supplier providing the sticky session identifier, or {@code null} if sticky routing is not used.
         */
        public SofaRpcOutboundRequest(SofaRequest request, StickyRequest stickyRequest) {
            super(request);
            this.stickyRequest = stickyRequest;
            this.service = request.getInterfaceName();
            String uniqueName = request.getTargetServiceUniqueName();
            int pos = uniqueName.lastIndexOf(':');
            this.group = pos < 0 ? null : uniqueName.substring(pos + 1);
            this.method = request.getMethodName();
            this.arguments = request.getMethodArgs();
            this.attachments = request.getRequestProps();
        }

        @Override
        public String getStickyId() {
            return stickyRequest == null ? null : stickyRequest.getStickyId();
        }

        @Override
        public void setStickyId(String stickyId) {
            if (stickyRequest != null) {
                stickyRequest.setStickyId(stickyId);
            }
        }
    }
}
