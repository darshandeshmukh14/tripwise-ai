package com.tripwise.dto;

import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.TransportMode;
import com.tripwise.entity.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param stops     multi-city stops in visiting order; empty for a single destination
 * @param itinerary latest itinerary, or {@code null} if none has been generated yet
 */
public record TripDetailResponse(
        Long id,
        String destination,
        String origin,
        LocalDate startDate,
        LocalDate endDate,
        Integer travellersCount,
        BigDecimal budgetAmount,
        String currency,
        String travelStyle,
        ComfortLevel comfortLevel,
        List<TransportMode> transportModes,
        TripStatus status,
        List<String> preferences,
        List<TripStopResponse> stops,
        Instant createdAt,
        Instant updatedAt,
        ItineraryResponse itinerary
) {
}
