package com.tripwise.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param currency      currency of all amounts of this version
 * @param fxRate        units of {@code currency} per 1 INR used for this version ({@code null} before v2)
 * @param costBreakdown itemised estimate for this version; {@code null} for plans generated before cost items existed
 */
public record ItineraryResponse(
        Long id,
        Integer version,
        BigDecimal totalEstimatedCost,
        String currency,
        BigDecimal fxRate,
        Instant fxAsOf,
        JsonNode plan,
        CostBreakdownResponse costBreakdown,
        Instant createdAt,
        Instant updatedAt
) {
}
