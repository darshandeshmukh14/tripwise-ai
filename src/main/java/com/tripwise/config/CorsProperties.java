package com.tripwise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Browser origins allowed to call the API directly. The Next.js BFF calls server-to-server, so production
 * usually needs none; local development may allow the Next.js dev server.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).toList();
    }
}
