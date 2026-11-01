package com.tripwise.dto;

import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.TransportMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Editable trip fields shared by create and update requests.
 */
public interface TripInput {

    int MAX_STOPS = 6;

    String destination();

    String origin();

    LocalDate startDate();

    LocalDate endDate();

    Integer travellersCount();

    BigDecimal budgetAmount();

    String currency();

    String travelStyle();

    ComfortLevel comfortLevel();

    List<TransportMode> transportModes();

    List<String> preferences();

    /**
     * Multi-city stops in visiting order; empty or omitted means a single destination.
     */
    List<TripStopRequest> stops();

    /**
     * When true the AI service may reorder the stops to the shortest valid route.
     */
    Boolean optimizeRouteOrder();

    /**
     * Stops are optional, but a multi-city trip needs 2..6 stops whose nights add up to the trip's nights.
     */
    static boolean stopsConsistent(List<TripStopRequest> stops, LocalDate startDate, LocalDate endDate) {
        if (stops == null || stops.isEmpty()) {
            return true;
        }
        if (stops.size() < 2 || stops.size() > MAX_STOPS || startDate == null || endDate == null
                || stops.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        long sum = stops.stream().mapToLong(s -> s.nights() == null ? 0 : s.nights()).sum();
        return sum == nights;
    }
}
