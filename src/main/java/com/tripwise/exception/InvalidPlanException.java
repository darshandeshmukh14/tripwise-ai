package com.tripwise.exception;

public class InvalidPlanException extends RuntimeException {

    public InvalidPlanException() {
        super("The trip plan is invalid or was modified. Please plan the trip again.");
    }
}
