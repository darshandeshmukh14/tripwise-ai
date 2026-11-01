package com.tripwise.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param countryCode ISO 3166-1 alpha-2 hint for ambiguous city names; resolved by the AI service when omitted
 */
public record TripStopRequest(
        @NotBlank @Size(max = 255) String city,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code") String countryCode,
        @NotNull @Positive @Max(59) Integer nights
) {
}
