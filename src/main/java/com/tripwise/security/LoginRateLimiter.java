package com.tripwise.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripwise.config.RateLimitProperties;
import com.tripwise.exception.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Per-account login throttle (in memory, single instance). Complements the per-IP limit in
 * {@link RateLimitFilter}, which cannot tell users apart when every request arrives from the BFF.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final RateLimitProperties properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    /**
     * @param email normalised (trimmed, lower-case) email of the login attempt
     * @throws TooManyRequestsException when the account has no attempts left in the current window
     */
    public void checkAttempt(String email) {
        if (!properties.enabled()) {
            return;
        }
        RateLimitProperties.Rule rule = properties.loginEmail();
        ConsumptionProbe probe = buckets.get(email, k -> RateLimitFilter.newBucket(rule))
                .tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(
                    Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1));
        }
    }
}
