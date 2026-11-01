package com.tripwise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param expectedVersion itinerary version the client is refining (optional); a mismatch returns 409
 */
public record SendMessageRequest(@NotBlank @Size(max = 2000) String content, @Positive Integer expectedVersion) {
}
