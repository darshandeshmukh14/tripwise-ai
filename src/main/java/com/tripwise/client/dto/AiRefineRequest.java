package com.tripwise.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Body of {@code POST /internal/v1/itineraries/refine}.
 *
 * @param history previous chat turns, oldest first (bounded by the caller)
 */
public record AiRefineRequest(
        Long tripId,
        AiGenerateTripRequest trip,
        JsonNode currentPlan,
        List<Turn> history,
        String message
) {

    /**
     * @param role USER or ASSISTANT
     */
    public record Turn(String role, String content) {
    }
}
