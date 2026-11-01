package com.tripwise.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret             Base64-encoded HMAC key (at least 256 bits)
 * @param expiration         access token lifetime in milliseconds
 * @param refreshExpiration  refresh token lifetime
 * @param refreshReuseGrace  how long a rotated refresh token is still accepted (concurrent refresh requests)
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expiration, @NotNull Duration refreshExpiration,
                            @NotNull Duration refreshReuseGrace) {

    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiration=" + expiration + ", refreshExpiration=" + refreshExpiration
                + ", refreshReuseGrace=" + refreshReuseGrace + "]";
    }
}
