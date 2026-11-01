package com.tripwise.security;

import com.tripwise.config.RateLimitProperties;
import com.tripwise.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final RateLimitProperties.Rule LOOSE = new RateLimitProperties.Rule(100, Duration.ofMinutes(1));

    @Test
    void checkAttempt_overLimitForOneEmail_throwsWithRetryAfter() {
        LoginRateLimiter limiter = new LoginRateLimiter(properties(true));
        limiter.checkAttempt("alice@example.com");
        limiter.checkAttempt("alice@example.com");

        assertThatThrownBy(() -> limiter.checkAttempt("alice@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isPositive());
    }

    @Test
    void checkAttempt_otherEmailsHaveTheirOwnBucket() {
        LoginRateLimiter limiter = new LoginRateLimiter(properties(true));
        limiter.checkAttempt("alice@example.com");
        limiter.checkAttempt("alice@example.com");

        assertThatCode(() -> limiter.checkAttempt("bob@example.com")).doesNotThrowAnyException();
    }

    @Test
    void checkAttempt_disabled_neverThrows() {
        LoginRateLimiter limiter = new LoginRateLimiter(properties(false));

        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                limiter.checkAttempt("alice@example.com");
            }
        }).doesNotThrowAnyException();
    }

    private static RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(enabled, LOOSE, new RateLimitProperties.Rule(2, Duration.ofMinutes(1)), LOOSE,
                LOOSE);
    }
}
