package com.tripwise.exception;

public class ItineraryNotReadyException extends RuntimeException {

    public ItineraryNotReadyException() {
        super("Generate an itinerary for this trip before chatting about it.");
    }
}
