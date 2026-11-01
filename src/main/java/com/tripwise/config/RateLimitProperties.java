package com.tripwise.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param auth       per client IP on POST /api/v1/auth/login and /register (coarse; behind the BFF without a
 *                   trusted proxy all clients share one IP, so keep it loose)
 * @param loginEmail per email on login attempts (credential-stuffing protection that does not depend on the IP)
 * @param ai         per user on AI-backed endpoints (generate, clarify, chat, stop suggestions) to protect Gemini
 *                   quota and cost
 * @param webHints   per user on Gemini web-search price hints (grounded calls are the most expensive); when the
 *                   budget is used up plans fall back to estimates instead of failing
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(boolean enabled, @NotNull @Valid Rule auth, @NotNull @Valid Rule loginEmail,
                                  @NotNull @Valid Rule ai, @NotNull @Valid Rule webHints) {

    /**
     * Allows {@code capacity} requests per {@code period}, refilled gradually.
     */
    public record Rule(@Positive long capacity, @NotNull Duration period) {
    }
}
