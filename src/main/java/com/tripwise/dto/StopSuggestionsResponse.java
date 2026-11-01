package com.tripwise.dto;

import com.tripwise.entity.Season;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nearby places that can be added to a trip (each within 400 km by road of an existing stop).
 */
public record StopSuggestionsResponse(List<Suggestion> suggestions) {

    /**
     * Extra cost fields estimate the stay, daily spend and one-way travel in {@code currency}.
     *
     * @param nearStop existing stop the place is closest to
     */
    public record Suggestion(
            String city,
            String countryCode,
            String nearStop,
            BigDecimal distanceKm,
            BigDecimal travelHours,
            Integer suggestedNights,
            BigDecimal extraCostMin,
            BigDecimal extraCostExpected,
            BigDecimal extraCostMax,
            String currency,
            Season season,
            String reason
    ) {
    }
}
