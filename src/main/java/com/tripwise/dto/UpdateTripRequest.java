package com.tripwise.dto;

import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Full replacement of a trip's editable fields (PUT semantics).
 *
 * @param comfortLevel       defaults to STANDARD when omitted
 * @param transportModes     empty or omitted means any mode
 * @param currency           ISO 4217 code from {@link SupportedCurrencies}; defaults to INR
 * @param stops              multi-city stops (2..6, nights adding up to the trip); empty or omitted for one place
 * @param optimizeRouteOrder reorder stops to the shortest valid route
 */
public record UpdateTripRequest(
        @NotBlank @Size(max = 255) String destination,
        @NotBlank @Size(max = 255) String origin,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive @Max(50) Integer travellersCount,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal budgetAmount,
        @Size(max = 50) String travelStyle,
        ComfortLevel comfortLevel,
        @Size(max = 4) List<@NotNull TransportMode> transportModes,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> preferences,
        @Size(max = 3) String currency,
        @Size(max = TripInput.MAX_STOPS) List<@NotNull @Valid TripStopRequest> stops,
        Boolean optimizeRouteOrder
) implements TripInput {

    @AssertTrue(message = "currency is not supported")
    public boolean isCurrencySupported() {
        return SupportedCurrencies.isSupported(currency);
    }

    @AssertTrue(message = "a multi-city trip needs 2 to 6 stops whose nights add up to the trip's nights")
    public boolean isStopsConsistent() {
        return TripInput.stopsConsistent(stops, startDate, endDate);
    }

    @AssertTrue(message = "endDate must be on or after startDate")
    public boolean isEndDateOnOrAfterStartDate() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "trip can be at most 60 days long")
    public boolean isDurationWithinLimit() {
        return startDate == null || endDate == null || !endDate.isAfter(startDate.plusDays(59));
    }
}
