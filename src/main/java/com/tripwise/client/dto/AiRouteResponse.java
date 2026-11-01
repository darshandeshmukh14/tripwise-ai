package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors the AI service's {@code RouteResponse}: canonical stops in visiting order and the 400 km leg check.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRouteResponse(List<Stop> stops, List<Leg> legs, boolean valid, List<String> violations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stop(Integer seq, String city, String countryCode, Integer nights, boolean known) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(String fromCity, String toCity, BigDecimal roadKm, boolean withinLimit) {
    }
}
