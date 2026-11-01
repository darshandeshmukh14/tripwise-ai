package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @param updatedPlan full new plan when the message changed the itinerary, otherwise {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRefineResponse(String assistantMessage, AiItineraryResponse updatedPlan) {
}
