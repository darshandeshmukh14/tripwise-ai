package com.tripwise.dto;

/**
 * @param trip the trip with the (possibly refined) plan; {@code id} is {@code null} because nothing is stored
 */
public record GuestChatReplyResponse(String assistantMessage, boolean itineraryUpdated, TripDetailResponse trip) {
}
