package com.tripwise.dto;

import com.tripwise.entity.TripStatus;

public record TripStatusResponse(Long tripId, TripStatus status) {
}
