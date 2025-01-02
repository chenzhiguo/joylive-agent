/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jd.live.agent.governance.invoke.ratelimit.tokenbucket;

import com.jd.live.agent.governance.policy.service.limit.RateLimitPolicy;
import com.jd.live.agent.governance.policy.service.limit.SlidingWindow;

import static java.lang.Math.min;

/**
 * SmoothWarmupLimiter
 * <p>
 * Source code implementation borrows from Guava's com.google.common.util.concurrent.SmoothRateLimiter.SmoothWarmingUp
 * </p>
 *
 * @since 1.4.0
 */
public class SmoothWarmupLimiter extends TokenBucketLimiter {

    private static final String KEY_WARMUP_SECONDS = "warmupSeconds";

    private static final String KEY_COLD_FACTOR = "coldFactor";

    private static final double DEFAULT_COLD_FACTOR = 3.0D;

    private static final long DEFAULT_WARMUP_SECONDS = 5L;

    private long warmupMicros;
    /**
     * The slope of the line from the stable interval (when permits == 0), to the cold interval
     * (when permits == maxPermits)
     */
    private double slope;
    private double thresholdPermits;
    private double coldIntervalMicros;

    public SmoothWarmupLimiter(RateLimitPolicy limitPolicy, SlidingWindow slidingWindow) {
        super(limitPolicy, slidingWindow);
    }

    @Override
    protected void initialize() {
        this.warmupMicros = option.getPositive(KEY_WARMUP_SECONDS, DEFAULT_WARMUP_SECONDS) * MICROSECOND_OF_ONE_SECOND;
        this.thresholdPermits = 0.5 * warmupMicros / permitIntervalMicros;
        this.coldIntervalMicros = permitIntervalMicros * option.getPositive(KEY_COLD_FACTOR, DEFAULT_COLD_FACTOR);
        super.initialize();
        this.slope = (coldIntervalMicros - permitIntervalMicros) / (maxStoredPermits - thresholdPermits);
    }

    @Override
    protected double getMaxStoredPermits() {
        return thresholdPermits + 2.0 * warmupMicros / (permitIntervalMicros + coldIntervalMicros);
    }

    @Override
    protected long estimateStorePermitsWaitTime(double storedPermits, double targetPermits) {
        double availablePermitsAboveThreshold = storedPermits - thresholdPermits;
        long micros = 0;
        // measuring the integral on the right part of the function (the climbing line)
        if (availablePermitsAboveThreshold > 0.0) {
            double permitsAboveThresholdToTake = min(availablePermitsAboveThreshold, targetPermits);
            double length =
                    permitsToTime(availablePermitsAboveThreshold)
                            + permitsToTime(availablePermitsAboveThreshold - permitsAboveThresholdToTake);
            micros = (long) (permitsAboveThresholdToTake * length / 2.0);
            targetPermits -= permitsAboveThresholdToTake;
        }
        // measuring the integral on the left part of the function (the horizontal line)
        micros += (long) (permitIntervalMicros * targetPermits);
        return micros;
    }

    @Override
    protected double coolDownIntervalMicros() {
        return warmupMicros / maxStoredPermits;
    }

    private double permitsToTime(double permits) {
        return permitIntervalMicros + permits * slope;
    }
}
