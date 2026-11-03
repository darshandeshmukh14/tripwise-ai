package com.tripwise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Saves a plan made without an account to the signed-in user's trips.
 *
 * @param plan the {@code itinerary.plan} of the guest plan (re-validated before saving)
 */
public record ClaimTripRequest(
        @NotNull @Valid CreateTripRequest trip,
        @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @NotNull @Size(max = 500) String> answers,
        @NotNull JsonNode plan
) {
}
