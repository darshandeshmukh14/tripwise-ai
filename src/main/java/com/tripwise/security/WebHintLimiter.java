package com.tripwise.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripwise.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user budget for Gemini web-search price hints (in memory, single instance). Never blocks a request:
 * when the budget is used up the AI service is told to skip hints and price from estimates only.
 */
@Component
@RequiredArgsConstructor
public class WebHintLimiter {

    private final RateLimitProperties properties;
    private final Cache<Long, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofDays(2))
            .build();

    /**
     * @return true if this user may use web hints for one more plan or reply
     */
    public boolean tryConsume(Long userId) {
        if (!properties.enabled()) {
            return true;
        }
        RateLimitProperties.Rule rule = properties.webHints();
        return buckets.get(userId, k -> RateLimitFilter.newBucket(rule)).tryConsume(1);
    }
}
