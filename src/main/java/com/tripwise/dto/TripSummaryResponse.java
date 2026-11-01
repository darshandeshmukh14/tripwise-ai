package com.tripwise.dto;

import com.tripwise.entity.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TripSummaryResponse(
        Long id,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        BigDecimal budgetAmount,
        String currency
) {
}
