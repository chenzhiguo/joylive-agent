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
package com.jd.live.agent.plugin.router.gprc.loadbalance;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.jd.live.agent.bootstrap.exception.RejectException.RejectNoProviderException;
import com.jd.live.agent.bootstrap.logger.Logger;
import com.jd.live.agent.bootstrap.logger.LoggerFactory;
import com.jd.live.agent.core.util.cache.LazyObject;
import com.jd.live.agent.governance.exception.ServiceError;
import com.jd.live.agent.governance.invoke.InvocationContext;
import com.jd.live.agent.plugin.router.gprc.cluster.GrpcCluster;
import com.jd.live.agent.plugin.router.gprc.exception.GrpcException.GrpcServerException;
import com.jd.live.agent.plugin.router.gprc.exception.GrpcStatus;
import com.jd.live.agent.plugin.router.gprc.instance.GrpcEndpoint;
import com.jd.live.agent.plugin.router.gprc.request.GrpcRequest.GrpcOutboundRequest;
import com.jd.live.agent.plugin.router.gprc.request.invoke.GrpcInvocation.GrpcOutboundInvocation;
import com.jd.live.agent.plugin.router.gprc.response.GrpcResponse.GrpcOutboundResponse;
import io.grpc.*;
import io.grpc.LoadBalancer.PickResult;
import io.grpc.LoadBalancer.PickSubchannelArgs;
import io.grpc.LoadBalancer.Subchannel;
import io.grpc.Metadata.Key;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static com.jd.live.agent.plugin.router.gprc.instance.GrpcEndpoint.NO_ENDPOINT_AVAILABLE;

/**
 * Live request.
 */
public class LiveRequest<ReqT, RespT> extends PickSubchannelArgs {

    private static final Logger logger = LoggerFactory.getLogger(LiveRequest.class);

    public static final CallOptions.Key<LiveRequest<?, ?>> KEY_LIVE_REQUEST = CallOptions.Key.create("x-live-request");

    private static final String METHOD_NEW_BUILDER = "newBuilder";

    private final MethodDescriptor<ReqT, RespT> methodDescriptor;

    private final LazyObject<Method> methodCache = new LazyObject<>(this::getNewBuilder);

    private final InvocationContext context;

    private CallOptions callOptions;

    private Supplier<ClientCall<ReqT, RespT>> callSupplier;

    private Metadata headers;

    private ClientCall.Listener<RespT> responseListener;

    private int numMessages;

    private Boolean messageCompression;

    private ReqT message;

    private ClientCall<ReqT, RespT> clientCall;

    private CompletableFuture<GrpcOutboundResponse> future;

    private final GrpcOutboundRequest request;

    private final GrpcOutboundInvocation invocation;

    private LiveRouteResult routeResult;

    private int counter;

    public LiveRequest(MethodDescriptor<ReqT, RespT> methodDescriptor, InvocationContext context) {
        this.methodDescriptor = methodDescriptor;
        this.context = context;
        this.request = new GrpcOutboundRequest(this);
        this.invocation = new GrpcOutboundInvocation(request, context);
        this.future = new CompletableFuture<>();
    }

    public InvocationContext getContext() {
        return context;
    }

    public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
        return methodDescriptor;
    }

    public void setCallOptions(CallOptions callOptions) {
        this.callOptions = callOptions;
    }

    public void setCallSupplier(Supplier<ClientCall<ReqT, RespT>> callSupplier) {
        this.callSupplier = callSupplier;
    }

    public CallOptions getCallOptions() {
        return callOptions;
    }

    public String getMethodName() {
        return methodDescriptor.getBareMethodName();
    }

    public Metadata getHeaders() {
        return headers;
    }

    public void setHeaders(Metadata headers) {
        this.headers = headers;
    }

    public void setResponseListener(ClientCall.Listener<RespT> responseListener) {
        this.responseListener = responseListener;
    }

    public void setNumMessages(int numMessages) {
        this.numMessages = numMessages;
    }

    public void setMessageCompression(boolean messageCompression) {
        this.messageCompression = messageCompression;
    }

    public void setMessage(ReqT message) {
        this.message = message;
    }

    public String getPath() {
        return methodDescriptor.getServiceName();
    }

    public ClientCall<ReqT, RespT> getClientCall() {
        return clientCall;
    }

    public void setClientCall(ClientCall<ReqT, RespT> clientCall) {
        this.clientCall = clientCall;
    }

    public LiveRouteResult getRouteResult() {
        return routeResult;
    }

    /**
     * Resets the state for a retry attempt.
     */
    public void onRetry() {
        this.routeResult = null;
        // cancel the last stream
        clientCall.cancel("retry", null);
        // recreate client call for retry
        counter++;
        future = new CompletableFuture<>();
        clientCall = callSupplier.get();
        if (messageCompression != null) {
            clientCall.setMessageCompression(messageCompression);
        }
        start();
        clientCall.request(numMessages);
    }

    /**
     * Starts the client call with the specified response listener and metadata headers.
     */
    public void start() {
        clientCall.start(new LiveCallListener<>(responseListener, future), headers);
    }

    /**
     * Sends a message and handles the response or any exceptions that may occur.
     *
     * @param message The message to be sent.
     */
    public void sendMessage(ReqT message) {
        this.message = message;
        GrpcCluster.INSTANCE.invoke(invocation).whenComplete(new LiveCompletion<>(clientCall, responseListener, future));
    }

    /**
     * Sends a message using the client call and returns a CompletionStage for the response.
     *
     * @return A CompletionStage that will be completed with the GrpcOutboundResponse when the message is processed.
     */
    public CompletionStage<GrpcOutboundResponse> sendMessage() {
        CompletableFuture<GrpcOutboundResponse> result = future;
        if (counter == 0) {
            sendMessage(clientCall, result);
        } else {
            sendMessageOnRetry(clientCall, result);
        }
        return result;
    }

    /**
     * Sends a message using the specified client call and future.
     *
     * @param call   The client call to use for sending the message.
     * @param future The CompletableFuture to complete with the GrpcOutboundResponse.
     */
    private void sendMessage(ClientCall<ReqT, RespT> call, CompletableFuture<GrpcOutboundResponse> future) {
        try {
            call.sendMessage(message);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Sends a message using the specified client call and future.
     *
     * @param call   The client call to use for sending the message.
     * @param future The CompletableFuture to complete with the GrpcOutboundResponse.
     */
    private void sendMessageOnRetry(ClientCall<ReqT, RespT> call, CompletableFuture<GrpcOutboundResponse> future) {
        try {
            call.sendMessage(message);
            call.halfClose();
        } catch (Throwable e) {
            call.cancel(e.getMessage(), e);
            future.completeExceptionally(e);
        }
    }

    /**
     * Sets a header with the specified key and value.
     *
     * @param key   The key of the header to be set.
     * @param value The value of the header to be set.
     */
    public void setHeader(String key, String value) {
        if (headers != null && key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            headers.put(Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
        }
    }

    /**
     * Routes the request to an appropriate endpoint from the provided list of endpoints.
     *
     * @param endpoints The list of available endpoint.
     */
    public void route(List<GrpcEndpoint> endpoints) {
        try {
            GrpcEndpoint endpoint = context.route(invocation, endpoints);
            routeResult = new LiveRouteResult(endpoint);
        } catch (Throwable e) {
            routeResult = new LiveRouteResult(e);
        }
    }

    /**
     * Routes the request based on the provided pick result.
     *
     * @param pickResult The pick result containing the status and subchannel information.
     */
    public void route(PickResult pickResult) {
        Status status = pickResult.getStatus();
        if (status.isOk()) {
            Subchannel subchannel = pickResult.getSubchannel();
            if (subchannel != null) {
                LiveRef ref = subchannel.getAttributes().get(LiveRef.KEY_STATE);
                routeResult = new LiveRouteResult(ref != null ? ref.getEndpoint() : new GrpcEndpoint(subchannel));
            } else {
                logger.warn("[LiveRequest]Subchannel is null, service {}", request.getService());
                routeResult = new LiveRouteResult(RejectNoProviderException.ofService(request.getService()));
            }
        } else {
            String description = status.getDescription();
            if (NO_ENDPOINT_AVAILABLE.equals(description)) {
                logger.warn("[LiveRequest]No endpoint available, service {}", request.getService());
                routeResult = new LiveRouteResult(RejectNoProviderException.ofService(request.getService()));
            } else {
                routeResult = new LiveRouteResult(status.asRuntimeException());
            }
        }
    }

    /**
     * Recovers from a degraded state by closing the client call.
     * <p>
     * If the client call is not null, this method will half-close the client call.
     */
    public void onRecover() {
        // recover from degrade
        // clientCall.halfClose();
    }

    /**
     * Parses a JSON string into a gRPC message.
     * <p>
     * This method uses the 'newBuilder' method to create a new builder for the message type, then merges
     * the provided JSON string into the builder using the {@link JsonFormat} parser. The resulting message
     * is returned.
     *
     * @param json The JSON string to parse.
     * @return The parsed gRPC message.
     * @throws Throwable If any other exception occurs during the parsing process.
     */
    public Object parse(byte[] json) throws Throwable {
        Method method = methodCache.get();
        if (method == null) {
            throw new NoSuchMethodException("method 'newBuilder' is not found in " + request.getPath());
        } else if (method.getReturnType() == void.class) {
            return null;
        }
        try {
            Message.Builder builder = (Message.Builder) method.invoke(null);
            JsonFormat.parser().merge(new InputStreamReader(new ByteArrayInputStream(json)), builder);
            return builder.build();
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /**
     * Retrieves the 'newBuilder' method.
     * If the method is not found or an exception occurs, it returns null.
     *
     * @return The 'newBuilder' method if found, otherwise null.
     */
    private Method getNewBuilder() {
        try {
            MethodDescriptor.Marshaller<?> marshaller = methodDescriptor.getResponseMarshaller();
            Class<?> type = marshaller.getClass();
            Field field = type.getDeclaredField("defaultInstance");
            field.setAccessible(true);
            Object instance = field.get(marshaller);
            Method method = instance.getClass().getMethod(METHOD_NEW_BUILDER);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * A listener for handling gRPC client call events.
     *
     * @param <RespT> The type of the response message.
     */
    private static class LiveCallListener<RespT> extends ClientCall.Listener<RespT> {

        private final ClientCall.Listener<RespT> listener;

        private final CompletableFuture<GrpcOutboundResponse> future;

        LiveCallListener(ClientCall.Listener<RespT> listener, CompletableFuture<GrpcOutboundResponse> future) {
            this.listener = listener;
            this.future = future;
        }

        @Override
        public void onMessage(RespT message) {
            future.complete(new GrpcOutboundResponse(message, true));
        }

        @Override
        public void onHeaders(Metadata headers) {
            // called before onMessage
            listener.onHeaders(headers);
        }

        @Override
        public void onReady() {
            listener.onReady();
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            if (!status.isOk()) {
                if (!future.isDone()) {
                    // retry
                    GrpcServerException exception = new GrpcServerException(status.asRuntimeException(trailers), status, trailers);
                    ServiceError error = new ServiceError(exception, key -> trailers == null ? null : trailers.get(Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                    GrpcOutboundResponse response = new GrpcOutboundResponse(error, null, status, trailers);
                    future.complete(response);
                } else {
                    // failed to retry client exception
                    listener.onClose(status, trailers);
                }
            } else {
                // Close when successful.
                listener.onClose(status, trailers);
            }
        }

    }

    /**
     * A BiConsumer for handling the completion of a gRPC outbound response.
     *
     * @param <ReqT>  The type of the request message.
     * @param <RespT> The type of the response message.
     */
    private static class LiveCompletion<ReqT, RespT> implements BiConsumer<GrpcOutboundResponse, Throwable> {

        private final ClientCall<ReqT, RespT> clientCall;

        private final ClientCall.Listener<RespT> listener;

        private final CompletableFuture<GrpcOutboundResponse> future;

        LiveCompletion(ClientCall<ReqT, RespT> clientCall,
                       ClientCall.Listener<RespT> listener,
                       CompletableFuture<GrpcOutboundResponse> future) {
            this.clientCall = clientCall;
            this.listener = listener;
            this.future = future;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void accept(GrpcOutboundResponse response, Throwable throwable) {
            // Just handle the exceptions, the success response has already been handled in the listener.
            ServiceError error = response == null ? null : response.getError();
            if (throwable != null) {
                onException(throwable);
            } else if (error != null && error.getThrowable() != null) {
                onException(error.getThrowable());
            } else if (error != null) {
                // server error
                listener.onClose(Status.UNKNOWN.withDescription(error.getError()), new Metadata());
            } else if (response != null && response.isServer()) {
                // server response
                listener.onMessage((RespT) response.getResponse());
            } else if (response != null) {
                // client response by degrade.
                listener.onMessage((RespT) response.getResponse());
                listener.onClose(Status.OK, response.getTrailers());
            }
        }

        /**
         * Handles an exception by notifying the listener with the appropriate status and trailers.
         *
         * @param throwable The throwable that occurred during the gRPC call.
         */
        private void onException(Throwable throwable) {
            if (!future.isDone()) {
                // throw client exception
                future.completeExceptionally(throwable);
                if (throwable instanceof StatusRuntimeException) {
                    throw (StatusRuntimeException) throwable;
                } else {
                    throw new StatusRuntimeException(GrpcStatus.createException(throwable));
                }
            } else {
                // server error
                GrpcStatus status = GrpcStatus.from(throwable);
                if (status != null) {
                    listener.onClose(status.getStatus(), status.getTrailers());
                } else {
                    listener.onClose(Status.UNKNOWN.withDescription(throwable.getMessage()).withCause(throwable), new Metadata());
                }
            }
        }
    }
}
