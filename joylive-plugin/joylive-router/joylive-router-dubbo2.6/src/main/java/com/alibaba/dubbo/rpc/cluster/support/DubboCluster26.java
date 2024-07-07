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
package com.alibaba.dubbo.rpc.cluster.support;


import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import com.jd.live.agent.bootstrap.exception.LiveException;
import com.jd.live.agent.bootstrap.exception.RejectException;
import com.jd.live.agent.bootstrap.exception.RejectException.RejectNoProviderException;
import com.jd.live.agent.core.util.Futures;
import com.jd.live.agent.core.util.network.Ipv4;
import com.jd.live.agent.core.util.type.ClassDesc;
import com.jd.live.agent.core.util.type.ClassUtils;
import com.jd.live.agent.core.util.type.FieldDesc;
import com.jd.live.agent.core.util.type.FieldList;
import com.jd.live.agent.governance.exception.RetryException.RetryExhaustedException;
import com.jd.live.agent.governance.instance.Endpoint;
import com.jd.live.agent.governance.invoke.OutboundInvocation;
import com.jd.live.agent.governance.invoke.cluster.ClusterInvoker;
import com.jd.live.agent.governance.invoke.cluster.LiveCluster;
import com.jd.live.agent.governance.policy.service.cluster.ClusterPolicy;
import com.jd.live.agent.governance.policy.service.cluster.RetryPolicy;
import com.jd.live.agent.governance.response.Response;
import com.jd.live.agent.plugin.router.dubbo.v2_6.instance.DubboEndpoint;
import com.jd.live.agent.plugin.router.dubbo.v2_6.request.DubboRequest.DubboOutboundRequest;
import com.jd.live.agent.plugin.router.dubbo.v2_6.response.DubboResponse.DubboOutboundResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


/**
 * Implements the {@link LiveCluster} interface for handling Dubbo outbound requests,
 * responses, and routing them to Dubbo endpoints. This class is specific to Dubbo's
 * RPC mechanism and provides the functionality to manage sticky sessions, route
 * requests, and handle invocation logic.
 * <p>
 * The class wraps around an {@link AbstractClusterInvoker} to leverage Dubbo's
 * clustering mechanism for routing and invoking RPC requests.
 * </p>
 */
public class DubboCluster26 implements LiveCluster<DubboOutboundRequest, DubboOutboundResponse, DubboEndpoint<?>, RpcException> {

    private final AbstractClusterInvoker cluster;

    private final AtomicBoolean destroyed;

    /**
     * The identifier used for stickiness. This ID is used to route requests to
     * the same provider consistently.
     */
    private String stickyId;

    /**
     * Constructs a new LiveCluster that wraps an abstract cluster.
     *
     * @param cluster the abstract cluster to be wrapped by this live cluster
     */
    public DubboCluster26(AbstractClusterInvoker cluster) {
        this.cluster = cluster;
        ClassDesc describe = ClassUtils.describe(cluster.getClass());
        FieldList fieldList = describe.getFieldList();
        FieldDesc field = fieldList.getField("destroyed");
        this.destroyed = (AtomicBoolean) (field == null ? null : field.get(cluster));
    }

    @Override
    public String getStickyId() {
        return stickyId;
    }

    @Override
    public void setStickyId(String stickyId) {
        this.stickyId = stickyId;
    }

    @Override
    public ClusterPolicy getDefaultPolicy(DubboOutboundRequest request) {
        ClusterPolicy policy = new ClusterPolicy();
        if (cluster instanceof FailoverClusterInvoker) {
            // no retry interval in com.alibaba.dubbo.rpc.cluster.support.FailoverClusterInvoker
            RetryPolicy retryPolicy = new RetryPolicy();
            retryPolicy.setRetry(getRetries(RpcUtils.getMethodName(request.getRequest())));
            policy.setType(ClusterInvoker.TYPE_FAILOVER);
            policy.setRetryPolicy(retryPolicy);
        } else if (cluster instanceof FailfastClusterInvoker) {
            policy.setType(ClusterInvoker.TYPE_FAILFAST);
        } else if (cluster instanceof FailsafeClusterInvoker) {
            policy.setType(ClusterInvoker.TYPE_FAILSAFE);
        } else if (cluster instanceof FailbackClusterInvoker) {
            policy.setType(ClusterInvoker.TYPE_FAILFAST);
        } else if (cluster instanceof BroadcastClusterInvoker) {
            policy.setType(null);
        } else if (cluster instanceof ForkingClusterInvoker) {
            policy.setType(ClusterInvoker.TYPE_FAILFAST);
        } else {
            policy.setType(ClusterInvoker.TYPE_FAILFAST);
        }
        return policy;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<List<DubboEndpoint<?>>> route(DubboOutboundRequest request) {
        try {
            List<Invoker<?>> invokers = cluster.list(request.getRequest());
            return CompletableFuture.completedFuture(invokers == null
                    ? new ArrayList<>()
                    : invokers.stream().map(DubboEndpoint::of).collect(Collectors.toList()));
        } catch (RpcException e) {
            return Futures.future(e);
        }
    }

    @Override
    public CompletionStage<DubboOutboundResponse> invoke(DubboOutboundRequest request, DubboEndpoint<?> endpoint) {
        try {
            Result result = endpoint.getInvoker().invoke(request.getRequest());
            DubboOutboundResponse response = result.hasException()
                    ? new DubboOutboundResponse(result, result.getException(), this::isRetryable)
                    : new DubboOutboundResponse(result);
            return CompletableFuture.completedFuture(response);
        } catch (RpcException e) {
            return CompletableFuture.completedFuture(new DubboOutboundResponse(e, this::isRetryable));
        }
    }

    @Override
    public DubboOutboundResponse createResponse(Throwable throwable, DubboOutboundRequest request, DubboEndpoint<?> endpoint) {
        if (throwable == null) {
            return new DubboOutboundResponse(new RpcResult());
        }
        return new DubboOutboundResponse(createException(throwable, request, endpoint), this::isRetryable);
    }

    @Override
    public boolean isRetryable(Response response) {
        if (response.getResponse() == null) {
            return true;
        } else if (!(response.getThrowable() instanceof RpcException)) {
            return false;
        } else {
            RpcException exception = (RpcException) response.getThrowable();
            return exception.isNetwork() || exception.isTimeout();
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed != null && destroyed.get();
    }

    @Override
    public RpcException createUnReadyException(DubboOutboundRequest request) {
        return createUnReadyException("Rpc cluster invoker for " + cluster.getInterface()
                + " on consumer " + Ipv4.getLocalHost()
                + " use dubbo version " + Version.getVersion()
                + " is not ready! Can not invoke any more.", request);
    }

    @Override
    public RpcException createUnReadyException(String message, DubboOutboundRequest request) {
        return new RpcException(message);
    }

    @Override
    public RpcException createException(Throwable throwable, DubboOutboundRequest request, DubboEndpoint<?> endpoint) {
        if (throwable == null) {
            return null;
        } else if (throwable instanceof RpcException) {
            return (RpcException) throwable;
        } else {
            String message = getError(throwable, request, endpoint);
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            if (throwable instanceof LiveException) {
                return new RpcException(RpcException.UNKNOWN_EXCEPTION, message);
            }
            return new RpcException(RpcException.UNKNOWN_EXCEPTION, message, cause);
        }
    }

    @Override
    public RpcException createNoProviderException(DubboOutboundRequest request) {
        Invocation invocation = request.getRequest();
        return new RpcException("Failed to invoke the method "
                + invocation.getMethodName() + " in the service " + cluster.getInterface().getName()
                + ". No provider available for the service " + cluster.directory.getUrl().getServiceKey()
                + " from registry " + cluster.directory.getUrl().getAddress()
                + " on the consumer " + NetUtils.getLocalHost()
                + " using the dubbo version " + Version.getVersion()
                + ". Please check if the providers have been started and registered.");
    }

    @Override
    public RpcException createLimitException(RejectException exception, DubboOutboundRequest request) {
        return new RpcException(RpcException.FORBIDDEN_EXCEPTION, exception.getMessage());
    }

    @Override
    public RpcException createCircuitBreakException(RejectException exception, DubboOutboundRequest request) {
        return new RpcException(RpcException.FORBIDDEN_EXCEPTION, exception.getMessage());
    }

    @Override
    public RpcException createRejectException(RejectException exception, DubboOutboundRequest request) {
        if (exception instanceof RejectNoProviderException) {
            return createNoProviderException(request);
        } else if (exception instanceof RejectException.RejectLimitException) {
            return createLimitException(exception, request);
        } else if (exception instanceof RejectException.RejectCircuitBreakException) {
            return createCircuitBreakException(exception, request);
        }
        return new RpcException(RpcException.FORBIDDEN_EXCEPTION, exception.getMessage());
    }

    @Override
    public RpcException createRetryExhaustedException(RetryExhaustedException exception, OutboundInvocation<DubboOutboundRequest> invocation) {
        String methodName = RpcUtils.getMethodName(invocation.getRequest().getRequest());
        Throwable cause = exception.getCause();
        RpcException le = cause instanceof RpcException ? (RpcException) cause : null;
        DubboOutboundRequest request = invocation.getRequest();
        Set<String> providers = request.getAttempts() == null ? new HashSet<>() : request.getAttempts();
        List<? extends Endpoint> instances = invocation.getInstances();
        return new RpcException(le != null ? le.getCode() : 0, "Failed to invoke the method "
                + methodName + " in the service " + cluster.getInterface().getName()
                + ". Tried " + exception.getAttempts() + " times of the providers " + providers
                + " (" + providers.size() + "/" + (instances == null ? 0 : instances.size())
                + ") from the registry " + cluster.directory.getUrl().getAddress()
                + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                + Version.getVersion() + ". Last error is: "
                + (le != null ? le.getMessage() : ""), le != null && le.getCause() != null ? le.getCause() : le);
    }

    /**
     * Retrieves the configured number of retries for a specific method invocation.
     *
     * @param methodName The name of the method for which the retry count is to be retrieved.
     * @return The number of retries configured for the specified method, defaulting to 1 if the
     * configured value is less than or equal to 0.
     */
    private int getRetries(String methodName) {
        int len = cluster.getUrl().getMethodParameter(methodName, Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES);
        if (len <= 0) {
            len = 1;
        }
        return len;
    }

    /**
     * Constructs a detailed error message for a given throwable and RPC call context.
     *
     * @param throwable The {@code Throwable} that represents the error encountered.
     * @param request   The {@code DubboOutboundRequest} that contains details about the RPC request.
     * @param endpoint  The {@code DubboEndpoint} that contains details about the endpoint being called.
     * @return A {@code String} representing the detailed error message.
     */
    private String getError(Throwable throwable, DubboOutboundRequest request, DubboEndpoint<?> endpoint) {
        if (endpoint == null) {
            return throwable.getMessage();
        }
        Invocation invocation = request.getRequest();
        return "Failed to call " + invocation.getInvoker().getInterface().getName() + "." + invocation.getMethodName()
                + " on remote server: " + endpoint.getInvoker().getUrl().getAddress() + ", cause by: "
                + throwable.getClass().getName() + ", message is: " + throwable.getMessage();
    }

}

