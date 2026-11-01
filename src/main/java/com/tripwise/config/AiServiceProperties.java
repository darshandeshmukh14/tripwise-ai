package com.tripwise.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ai-service")
public record AiServiceProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    @Override
    public String toString() {
        return "AiServiceProperties[baseUrl=" + baseUrl + ", apiKey=***, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
