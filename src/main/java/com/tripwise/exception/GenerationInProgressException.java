package com.tripwise.exception;

public class GenerationInProgressException extends RuntimeException {

    public GenerationInProgressException() {
        super("Itinerary generation is already in progress for this trip.");
    }
}
