package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.TransportMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Trip input sent to the AI service (generate, clarify, refine and stop suggestions). Contains no user PII.
 *
 * @param answers       answers to clarifying questions keyed by question id; empty when none were asked
 * @param stops         multi-city stops in visiting order; empty for a single destination
 * @param allowWebHints whether the user still has web price hint quota (decided by Spring, see WebHintLimiter)
 */
public record AiGenerateTripRequest(
        Long tripId,
        String origin,
        String destination,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate endDate,
        Integer travellersCount,
        BigDecimal budgetAmount,
        String currency,
        String travelStyle,
        ComfortLevel comfortLevel,
        List<TransportMode> transportModes,
        List<String> preferences,
        Map<String, String> answers,
        List<AiStop> stops,
        boolean allowWebHints
) {

    public AiGenerateTripRequest withAllowWebHints(boolean allow) {
        return new AiGenerateTripRequest(tripId, origin, destination, startDate, endDate, travellersCount,
                budgetAmount, currency, travelStyle, comfortLevel, transportModes, preferences, answers, stops, allow);
    }
}
