package com.tripwise.exception;

public class ItineraryConflictException extends RuntimeException {

    public ItineraryConflictException() {
        super("The itinerary changed while you were editing it. Reload and try again.");
    }
}
