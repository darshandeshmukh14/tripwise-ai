package com.tripwise.client.dto;

/**
 * Body of {@code POST /internal/v1/stops/suggest}.
 */
public record AiSuggestRequest(AiGenerateTripRequest trip, int limit) {
}
