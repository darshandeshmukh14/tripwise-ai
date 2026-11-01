package com.tripwise.exception;

import lombok.Getter;

@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
