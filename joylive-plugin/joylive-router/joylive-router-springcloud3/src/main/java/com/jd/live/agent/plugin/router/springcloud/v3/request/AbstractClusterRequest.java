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
package com.jd.live.agent.plugin.router.springcloud.v3.request;

import com.jd.live.agent.core.util.cache.CacheObject;
import com.jd.live.agent.governance.request.AbstractHttpRequest.AbstractHttpOutboundRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.jd.live.agent.core.util.type.ClassUtils.getValue;

/**
 * Represents an outbound HTTP request in a reactive microservices architecture,
 * extending the capabilities of an abstract HTTP outbound request model to include
 * client-specific functionalities. This class encapsulates features such as load balancing,
 * service instance discovery, and lifecycle management, making it suitable for handling
 * dynamic client requests in a distributed system.
 */
public abstract class AbstractClusterRequest<T> extends AbstractHttpOutboundRequest<T> implements SpringClusterRequest {

    protected static final String FIELD_SERVICE_INSTANCE_LIST_SUPPLIER_PROVIDER = "serviceInstanceListSupplierProvider";

    protected static final Map<String, Set<LoadBalancerLifecycle>> LOAD_BALANCER_LIFE_CYCLES = new ConcurrentHashMap<>();

    protected static final Map<String, CacheObject<ServiceInstanceListSupplier>> SERVICE_INSTANCE_LIST_SUPPLIERS = new ConcurrentHashMap<>();

    /**
     * A factory for creating instances of {@code ReactiveLoadBalancer} for service instances.
     * This factory is used to obtain a load balancer instance for the service associated with
     * this request.
     */
    protected final ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory;

    /**
     * A {@code LoadBalancerProperties} object, containing configuration
     * properties for load balancing.
     */
    protected final LoadBalancerProperties properties;

    /**
     * A lazy-initialized object of {@code Set<LoadBalancerLifecycle>}, representing the lifecycle
     * processors for the load balancer. These processors provide hooks for custom logic at various
     * stages of the load balancing process.
     */
    protected CacheObject<Set<LoadBalancerLifecycle>> lifecycles;

    /**
     * A lazy-initialized {@code Request<?>} object that encapsulates the original request data
     * along with any hints to influence load balancing decisions.
     */
    protected CacheObject<Request<?>> lbRequest;

    /**
     * A lazy-initialized {@code RequestData} object, representing the data of the original
     * request that will be used by the load balancer to select an appropriate service instance.
     */
    protected CacheObject<RequestData> requestData;

    /**
     * A lazy-initialized {@code ServiceInstanceListSupplier} object, responsible for providing
     * a list of available service instances for load balancing.
     */
    protected CacheObject<ServiceInstanceListSupplier> instanceSupplier;

    protected CacheObject<String> stickyId;

    public AbstractClusterRequest(T request,
                                  URI uri,
                                  ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory,
                                  LoadBalancerProperties properties) {
        super(request);
        this.uri = uri;
        this.loadBalancerFactory = loadBalancerFactory;
        // depend on url
        this.properties = buildProperties(properties);
    }

    @Override
    public String getCookie(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        } else if (request instanceof ServerHttpRequest) {
            ServerHttpRequest httpRequest = (ServerHttpRequest) request;
            HttpCookie cookie = httpRequest.getCookies().getFirst(key);
            return cookie == null ? null : cookie.getValue();
        } else {
            return super.getCookie(key);
        }
    }

    @Override
    public String getStickyId() {
        if (stickyId == null) {
            stickyId = new CacheObject<>(buildStickyId());
        }
        return stickyId.get();
    }

    @Override
    public void lifecycles(Consumer<LoadBalancerLifecycle> consumer) {
        Set<LoadBalancerLifecycle> lifecycles = getLifecycles();
        if (lifecycles != null && consumer != null) {
            lifecycles.forEach(consumer);
        }
    }

    public Set<LoadBalancerLifecycle> getLifecycles() {
        if (lifecycles == null) {
            lifecycles = new CacheObject<>(buildLifecycleProcessors());
        }
        return lifecycles.get();
    }

    public Request<?> getLbRequest() {
        if (lbRequest == null) {
            lbRequest = new CacheObject<>(buildLbRequest());
        }
        return lbRequest.get();
    }

    public LoadBalancerProperties getProperties() {
        return properties;
    }

    /**
     * Returns a supplier of service instance lists.
     *
     * @return a supplier of service instance lists
     */
    public ServiceInstanceListSupplier getInstanceSupplier() {
        if (instanceSupplier == null) {
            instanceSupplier = new CacheObject<>(buildServiceInstanceListSupplier());
        }
        return instanceSupplier.get();
    }

    public RequestData getRequestData() {
        if (requestData == null) {
            requestData = new CacheObject<>(buildRequestData());
        }
        return requestData.get();
    }

    /**
     * Creates a new {@code RequestData} object representing the data of the original request.
     * This abstract method must be implemented by subclasses to provide specific request data
     * for the load balancing process.
     *
     * @return a new {@code RequestData} object
     */
    protected abstract RequestData buildRequestData();

    /**
     * Builds the LoadBalancerProperties based on the default properties and the LoadBalancerFactory.
     *
     * @param defaultProperties the default LoadBalancerProperties
     * @return the built LoadBalancerProperties
     */
    protected LoadBalancerProperties buildProperties(LoadBalancerProperties defaultProperties) {
        try {
            LoadBalancerProperties result = loadBalancerFactory == null ? null : loadBalancerFactory.getProperties(getService());
            return result == null ? defaultProperties : result;
        } catch (Throwable e) {
            // fix spring cloud 3.0.6 without getProperties
            return defaultProperties;
        }
    }

    /**
     * Constructs a set of lifecycle processors for the load balancer. These processors are responsible
     * for providing custom logic that can be executed during various stages of the load balancing process,
     * such as before and after choosing a server, and before and after the request is completed.
     *
     * @return A set of LoadBalancerLifecycle objects that are compatible with the current service and request/response types.
     */
    protected Set<LoadBalancerLifecycle> buildLifecycleProcessors() {
        return LOAD_BALANCER_LIFE_CYCLES.computeIfAbsent(getService(), service -> loadBalancerFactory == null
                ? new HashSet<>()
                : LoadBalancerLifecycleValidator.getSupportedLifecycleProcessors(
                loadBalancerFactory.getInstances(service, LoadBalancerLifecycle.class),
                RequestDataContext.class,
                ResponseData.class,
                ServiceInstance.class));
    }

    /**
     * Creates a new load balancer request object that encapsulates the original request data along with
     * any hints that may influence load balancing decisions. This object is used by the load balancer to
     * select an appropriate service instance based on the provided hints and other criteria.
     *
     * @return A DefaultRequest object containing the context for the load balancing operation.
     */
    protected DefaultRequest<RequestDataContext> buildLbRequest() {
        LoadBalancerProperties properties = getProperties();
        Map<String, String> hints = properties == null ? null : properties.getHint();
        String defaultHint = hints == null ? null : hints.getOrDefault("default", "default");
        String hint = hints == null ? null : hints.getOrDefault(getService(), defaultHint);
        return new DefaultRequest<>(new RequestDataContext(getRequestData(), hint));
    }

    /**
     * Builds a supplier of service instances for load balancing. This supplier is responsible for providing
     * a list of available service instances that the load balancer can use to distribute the incoming requests.
     * The supplier is obtained from the load balancer instance if it provides one.
     *
     * @return A ServiceInstanceListSupplier that provides a list of available service instances, or null if the
     * load balancer does not provide such a supplier.
     */
    protected ServiceInstanceListSupplier buildServiceInstanceListSupplier() {
        return SERVICE_INSTANCE_LIST_SUPPLIERS.computeIfAbsent(getService(), service -> {
            ServiceInstanceListSupplier supplier = null;
            ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerFactory == null ? null : loadBalancerFactory.getInstance(getService());
            if (loadBalancer != null) {
                supplier = getServiceInstanceListSupplier(loadBalancer);
            }
            return CacheObject.of(supplier);
        }).get();

    }

    /**
     * Retrieves the ServiceInstanceListSupplier provider from the given ReactiveLoadBalancer.
     *
     * @param loadBalancer the ReactiveLoadBalancer to retrieve the ServiceInstanceListSupplier provider from
     * @return an instance of ServiceInstanceListSupplier
     */
    protected ServiceInstanceListSupplier getServiceInstanceListSupplier(ReactiveLoadBalancer<ServiceInstance> loadBalancer) {
        ObjectProvider<ServiceInstanceListSupplier> provider = getValue(loadBalancer, FIELD_SERVICE_INSTANCE_LIST_SUPPLIER_PROVIDER);
        return provider == null ? null : provider.getIfAvailable();
    }

    /**
     * Extracts the identifier from a sticky session cookie.
     *
     * @return The value of the sticky session cookie if present; otherwise, {@code null}.
     * This value is used to identify the server instance that should handle requests
     * from this client to ensure session persistence.
     */
    protected String buildStickyId() {
        LoadBalancerProperties properties = getProperties();
        if (properties != null) {
            String instanceIdCookieName = properties.getStickySession().getInstanceIdCookieName();
            Object context = getLbRequest().getContext();
            if (context instanceof RequestDataContext) {
                MultiValueMap<String, String> cookies = ((RequestDataContext) context).getClientRequest().getCookies();
                return cookies == null ? null : cookies.getFirst(instanceIdCookieName);
            }
        }
        return null;
    }
}
