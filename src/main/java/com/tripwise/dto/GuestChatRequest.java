package com.tripwise.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Chat refinement without an account: the browser sends the trip, the current plan and recent turns each time.
 *
 * @param plan    the {@code itinerary.plan} returned by the previous guest call
 * @param history previous turns, oldest first
 */
public record GuestChatRequest(
        @NotNull @Valid CreateTripRequest trip,
        @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @NotNull @Size(max = 500) String> answers,
        @NotNull JsonNode plan,
        @Size(max = 20) List<@NotNull @Valid Turn> history,
        @NotBlank @Size(max = 2000) String content
) {

    public record Turn(@NotNull @Pattern(regexp = "USER|ASSISTANT") String role,
                       @NotBlank @Size(max = 4000) String content) {
    }
}
