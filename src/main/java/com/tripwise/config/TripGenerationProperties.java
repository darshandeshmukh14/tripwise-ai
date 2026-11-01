package com.tripwise.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param staleAfter a trip still GENERATING this long after its last update is marked FAILED by the sweeper
 */
@Validated
@ConfigurationProperties(prefix = "trip-generation")
public record TripGenerationProperties(@NotNull Duration staleAfter) {
}
