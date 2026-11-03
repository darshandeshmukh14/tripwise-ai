package com.tripwise.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripwise.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user (or per client IP for guests) budget for Gemini web-search price hints (in memory, single instance).
 * Never blocks a request: when the budget is used up the AI service is told to skip hints and price from
 * estimates only.
 */
@Component
@RequiredArgsConstructor
public class WebHintLimiter {

    private final RateLimitProperties properties;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofDays(2))
            .build();

    /**
     * @return true if this user may use web hints for one more plan or reply
     */
    public boolean tryConsume(Long userId) {
        return consume("user:" + userId);
    }

    /**
     * @return true if this client IP (planning without an account) may use web hints for one more plan or reply
     */
    public boolean tryConsumeGuest(String clientIp) {
        return consume("guest:" + clientIp);
    }

    private boolean consume(String key) {
        if (!properties.enabled()) {
            return true;
        }
        RateLimitProperties.Rule rule = properties.webHints();
        return buckets.get(key, k -> RateLimitFilter.newBucket(rule)).tryConsume(1);
    }
}
