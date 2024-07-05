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
package com.jd.live.agent.governance.invoke.circuitbreak;

import com.jd.live.agent.core.inject.annotation.Inject;
import com.jd.live.agent.core.util.time.Timer;
import com.jd.live.agent.governance.policy.PolicyId;
import com.jd.live.agent.governance.policy.service.*;
import com.jd.live.agent.governance.policy.service.circuitbreaker.CircuitBreakerPolicy;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * AbstractCircuitBreakerFactory provides a base implementation for factories that create and manage circuit breakers.
 * It uses a thread-safe map to store and retrieve circuit breakers associated with specific circuit breaker policies.
 * This class is designed to be extended by concrete factory implementations that provide the actual
 * circuit breaker creation logic.
 *
 * @since 1.1.0
 */
public abstract class AbstractCircuitBreakerFactory implements CircuitBreakerFactory {

    /**
     * A thread-safe map to store circuit breakers associated with their respective policies.
     * The keys are the policy IDs, and the values are atomic references to the circuit breakers.
     */
    protected final Map<Long, AtomicReference<CircuitBreaker>> circuitBreakers = new ConcurrentHashMap<>();

    @Inject(Timer.COMPONENT_TIMER)
    private Timer timer;

    /**
     * Retrieves a circuit breaker for the given circuit breaker policy. If a circuit breaker for the policy
     * already exists and its version is greater than or equal to the policy version, it is returned.
     * Otherwise, a new circuit breaker is created using the {@link #create(CircuitBreakerPolicy)} method.
     *
     * @param policy      The circuit breaker policy for which to retrieve or create a circuit breaker.
     * @param serviceFunc A function that provides service.
     * @return A circuit breaker that corresponds to the given policy, or null if the policy is null.
     */
    @Override
    public CircuitBreaker get(CircuitBreakerPolicy policy, Function<String, Service> serviceFunc) {
        if (policy == null) {
            return null;
        }
        AtomicReference<CircuitBreaker> reference = circuitBreakers.computeIfAbsent(policy.getId(), n -> new AtomicReference<>());
        CircuitBreaker circuitBreaker = reference.get();
        if (circuitBreaker != null && circuitBreaker.getPolicy().getVersion() == policy.getVersion()) {
            return circuitBreaker;
        }
        CircuitBreaker breaker = create(policy);
        while (true) {
            circuitBreaker = reference.get();
            if (circuitBreaker == null || circuitBreaker.getPolicy().getVersion() != policy.getVersion()) {
                if (reference.compareAndSet(circuitBreaker, breaker)) {
                    circuitBreaker = breaker;
                    addRecycleTask(policy, serviceFunc);
                    break;
                }
            }
        }
        return circuitBreaker;
    }

    private void addRecycleTask(CircuitBreakerPolicy policy, Function<String, Service> serviceFunc) {
        long delay = 60000 + ThreadLocalRandom.current().nextInt(60000 * 4);
        timer.delay("recycle-circuitbreaker-" + policy.getId(), delay, () -> recycle(policy, serviceFunc));
    }

    private void recycle(CircuitBreakerPolicy policy, Function<String, Service> serviceFunc) {
        AtomicReference<CircuitBreaker> ref = circuitBreakers.get(policy.getId());
        CircuitBreaker circuitBreaker = ref == null ? null : ref.get();
        if (circuitBreaker != null && serviceFunc != null) {
            String serviceName = policy.getTag(PolicyId.KEY_SERVICE_NAME);
            String serviceGroup = policy.getTag(PolicyId.KEY_SERVICE_GROUP);
            String servicePath = policy.getTag(PolicyId.KEY_SERVICE_PATH);
            String serviceMethod = policy.getTag(PolicyId.KEY_SERVICE_METHOD);

            Service service = serviceFunc.apply(serviceName);
            ServiceGroup group = service == null ? null : service.getGroup(serviceGroup);
            ServicePath path = group == null ? null : group.getPath(servicePath);
            ServiceMethod method = path == null ? null : path.getMethod(serviceMethod);

            ServicePolicy servicePolicy = method != null ? method.getServicePolicy() : null;
            servicePolicy = servicePolicy == null && path != null ? path.getServicePolicy() : servicePolicy;
            servicePolicy = servicePolicy == null && group != null ? group.getServicePolicy() : servicePolicy;

            boolean exists = false;
            if (servicePolicy != null && servicePolicy.getCircuitBreakerPolicies() != null) {
                for (CircuitBreakerPolicy circuitBreakerPolicy : servicePolicy.getCircuitBreakerPolicies()) {
                    if (Objects.equals(circuitBreakerPolicy.getId(), policy.getId())) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                circuitBreakers.remove(policy.getId());
            } else {
                addRecycleTask(policy, serviceFunc);
            }
        }
    }

    /**
     * Creates a new circuit breaker instance based on the provided circuit breaker policy.
     * This method is abstract and must be implemented by subclasses to provide the specific
     * circuit breaker creation logic.
     *
     * @param policy The circuit breaker policy to be used for creating the circuit breaker.
     * @return A new circuit breaker instance that enforces the given policy.
     */
    protected abstract CircuitBreaker create(CircuitBreakerPolicy policy);

}
