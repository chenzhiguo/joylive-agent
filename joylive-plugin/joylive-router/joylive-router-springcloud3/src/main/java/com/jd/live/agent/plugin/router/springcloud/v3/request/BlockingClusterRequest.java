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

import com.jd.live.agent.core.util.http.HttpMethod;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.util.MultiValueMapAdapter;

import java.util.List;
import java.util.Map;

/**
 * Represents a blocking request in a routing context, extending the capabilities of {@link AbstractClusterRequest}
 * to handle HTTP requests in environments where reactive programming models are not used.
 *
 * @see AbstractClusterRequest for the base functionality
 */
public class BlockingClusterRequest extends AbstractClusterRequest<HttpRequest> {

    /**
     * The body of the HTTP request.
     */
    private final byte[] body;

    /**
     * The execution context for the client HTTP request, allowing for further processing or manipulation.
     */
    private final ClientHttpRequestExecution execution;

    private final HttpHeaders writeableHeaders;

    /**
     * Constructs a new {@code BlockingRouteRequest} with the specified request details and load balancing context.
     *
     * @param request             The original HTTP request.
     * @param loadBalancerFactory The factory to obtain a load balancer instance for routing decisions.
     * @param properties          The LoadBalancerProperties object containing the configuration for the load balancer.
     * @param body                The body of the request as a byte array.
     * @param execution           The execution context for processing the request.
     */
    public BlockingClusterRequest(HttpRequest request,
                                  ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory,
                                  LoadBalancerProperties properties,
                                  byte[] body,
                                  ClientHttpRequestExecution execution) {
        super(request, loadBalancerFactory, properties);
        this.uri = request.getURI();
        this.body = body;
        this.execution = execution;
        this.writeableHeaders = HttpHeaders.writableHttpHeaders(request.getHeaders());
    }

    @Override
    public HttpMethod getHttpMethod() {
        try {
            return HttpMethod.valueOf(request.getMethodValue());
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    @Override
    public String getHeader(String key) {
        return key == null || key.isEmpty() ? null : request.getHeaders().getFirst(key);
    }

    @Override
    public void setHeader(String key, String value) {
        if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
            writeableHeaders.set(key, value);
        }
    }

    @Override
    protected RequestData buildRequestData() {
        Map<String, List<String>> cookies = getCookies();
        return new RequestData(request.getMethod(), request.getURI(), request.getHeaders(),
                cookies == null ? null : new MultiValueMapAdapter<>(cookies), null);
    }

    public byte[] getBody() {
        return body;
    }

    public ClientHttpRequestExecution getExecution() {
        return execution;
    }

    @Override
    protected Map<String, List<String>> parseHeaders() {
        return writeableHeaders;
    }
}
