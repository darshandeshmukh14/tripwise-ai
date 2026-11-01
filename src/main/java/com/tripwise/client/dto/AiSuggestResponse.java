package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.entity.Season;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors the AI service's {@code SuggestResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSuggestResponse(List<Suggestion> suggestions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Suggestion(
            String city,
            String countryCode,
            String nearStop,
            BigDecimal distanceKm,
            BigDecimal travelHours,
            Integer suggestedNights,
            AmountRange extraCost,
            String currency,
            Season season,
            String reason
    ) {
    }
}
