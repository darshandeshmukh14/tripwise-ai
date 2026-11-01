package com.tripwise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Optional body of {@code POST /api/v1/trips/{id}/generate}.
 *
 * @param answers answers to clarifying questions keyed by question id
 */
public record GenerateTripRequest(
        @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @NotBlank @Size(max = 500) String> answers
) {
}
