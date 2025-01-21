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
package com.jd.live.agent.plugin.router.gprc.exception;

import com.jd.live.agent.bootstrap.exception.FaultException;
import com.jd.live.agent.bootstrap.exception.LiveException;
import com.jd.live.agent.bootstrap.exception.RejectException;
import com.jd.live.agent.bootstrap.exception.RejectException.*;
import com.jd.live.agent.governance.exception.RetryException.RetryExhaustedException;
import com.jd.live.agent.governance.exception.RetryException.RetryTimeoutException;
import com.jd.live.agent.plugin.router.gprc.exception.GrpcException.GrpcServerException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * A utility class that provides methods for creating gRPC Status objects from various types of exceptions.
 */
public class GrpcStatus {

    private final Status status;

    private final Metadata trailers;

    private final boolean server;

    public GrpcStatus(Status status, Metadata trailers, boolean server) {
        this.status = status;
        this.trailers = trailers;
        this.server = server;
    }

    public Status getStatus() {
        return status;
    }

    public Metadata getTrailers() {
        return trailers;
    }

    public boolean isServer() {
        return server;
    }

    public Integer getValue() {
        return status == null ? null : status.getCode().value();

    }

    /**
     * Creates a gRPC Status object from the given Throwable object.
     *
     * @param throwable The Throwable object to convert to a gRPC Status object.
     * @return The gRPC Status object corresponding to the given Throwable object.
     */
    public static Status createException(Throwable throwable) {
        if (throwable == null) {
            return Status.OK;
        } else if (throwable instanceof RejectUnreadyException) {
            return createUnReadyException((RejectUnreadyException) throwable);
        } else if (throwable instanceof RejectAuthException) {
            return createAuthException((RejectAuthException) throwable);
        } else if (throwable instanceof RejectPermissionException) {
            return createPermissionException((RejectPermissionException) throwable);
        } else if (throwable instanceof RejectEscapeException) {
            return createEscapeException((RejectEscapeException) throwable);
        } else if (throwable instanceof RejectLimitException) {
            return createLimitException((RejectLimitException) throwable);
        } else if (throwable instanceof RejectCircuitBreakException) {
            return createCircuitBreakException((RejectCircuitBreakException) throwable);
        } else if (throwable instanceof RejectException) {
            return createRejectException((RejectException) throwable);
        } else if (throwable instanceof FaultException) {
            return createFaultException((FaultException) throwable);
        } else if (throwable instanceof RejectNoProviderException) {
            return createNoProviderException((RejectNoProviderException) throwable);
        } else if (throwable instanceof RetryExhaustedException) {
            return createRetryExhaustedException((RetryExhaustedException) throwable);
        } else if (throwable instanceof RetryTimeoutException) {
            return createRetryTimeoutException((RetryTimeoutException) throwable);
        } else if (throwable instanceof LiveException) {
            return createLiveException((LiveException) throwable);
        } else {
            return createUnknownException(throwable);
        }
    }

    /**
     * Creates a gRPC Status object for a RejectUnreadyException.
     *
     * @param exception The RejectUnreadyException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectUnreadyException object.
     */
    protected static Status createUnReadyException(RejectUnreadyException exception) {
        return Status.UNAVAILABLE.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a LiveException.
     *
     * @param exception The LiveException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given LiveException object.
     */
    public static Status createLiveException(LiveException exception) {
        return Status.INTERNAL.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectPermissionException.
     *
     * @param exception The RejectPermissionException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectPermissionException object.
     */
    protected static Status createPermissionException(RejectPermissionException exception) {
        return Status.PERMISSION_DENIED.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectAuthException.
     *
     * @param exception The RejectAuthException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectAuthException object.
     */
    protected static Status createAuthException(RejectAuthException exception) {
        return Status.UNAUTHENTICATED.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectLimitException.
     *
     * @param exception The RejectLimitException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectLimitException object.
     */
    protected static Status createLimitException(RejectLimitException exception) {
        return Status.RESOURCE_EXHAUSTED.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectCircuitBreakException.
     *
     * @param exception The RejectCircuitBreakException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectCircuitBreakException object.
     */
    protected static Status createCircuitBreakException(RejectCircuitBreakException exception) {
        return Status.RESOURCE_EXHAUSTED.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectEscapeException.
     *
     * @param exception The RejectEscapeException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectEscapeException object.
     */
    protected static Status createEscapeException(RejectEscapeException exception) {
        return Status.OUT_OF_RANGE.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectException.
     *
     * @param exception The RejectException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectException object.
     */
    protected static Status createRejectException(RejectException exception) {
        return Status.INTERNAL.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a FaultException.
     *
     * @param exception The FaultException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given FaultException object.
     */
    protected static Status createFaultException(FaultException exception) {
        Status status = exception.getCode() == null ? Status.INTERNAL : Status.fromCodeValue(exception.getCode());
        return status.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RejectNoProviderException.
     *
     * @param exception The RejectNoProviderException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RejectNoProviderException object.
     */
    protected static Status createNoProviderException(RejectNoProviderException exception) {
        return Status.UNAVAILABLE.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RetryExhaustedException.
     *
     * @param exception The RetryExhaustedException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RetryExhaustedException object.
     */
    protected static Status createRetryExhaustedException(RetryExhaustedException exception) {
        return Status.UNAVAILABLE.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for a RetryTimeoutException.
     *
     * @param exception The RetryTimeoutException object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given RetryTimeoutException object.
     */
    protected static Status createRetryTimeoutException(RetryTimeoutException exception) {
        return Status.UNAVAILABLE.withDescription(exception.getMessage());
    }

    /**
     * Creates a gRPC Status object for an unknown exception.
     *
     * @param exception The Throwable object to convert to a gRPC Status object.
     * @return The gRPC Status object for the given Throwable object.
     */
    protected static Status createUnknownException(Throwable exception) {
        return Status.INTERNAL.withDescription(exception.getMessage());
    }

    /**
     * Converts a Throwable object to a GrpcStatus object.
     *
     * @param throwable the Throwable object to convert
     * @return a GrpcStatus object representing the status of the Throwable, or null if the Throwable cannot be converted
     */
    public static GrpcStatus from(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException exception = (StatusRuntimeException) throwable;
            return new GrpcStatus(exception.getStatus(), exception.getTrailers(), false);
        } else if (throwable instanceof StatusException) {
            StatusException exception = (StatusException) throwable;
            return new GrpcStatus(exception.getStatus(), exception.getTrailers(), false);
        } else if (throwable instanceof GrpcServerException) {
            GrpcServerException exception = (GrpcServerException) throwable;
            return new GrpcStatus(exception.getStatus(), exception.getTrailers(), true);
        }
        return null;
    }
}
