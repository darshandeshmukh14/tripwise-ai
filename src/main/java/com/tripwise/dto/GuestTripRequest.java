package com.tripwise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Trip details for planning without an account; nothing is stored on the server.
 *
 * @param answers answers to clarifying questions (optional); a "budget" answer overrides the budget
 */
public record GuestTripRequest(
        @NotNull @Valid CreateTripRequest trip,
        @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @NotNull @Size(max = 500) String> answers
) {
}
