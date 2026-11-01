package com.tripwise.dto;

/**
 * @param itineraryVersion current itinerary version after the reply (incremented when the plan changed)
 */
public record ChatReplyResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        boolean itineraryUpdated,
        Integer itineraryVersion
) {
}
