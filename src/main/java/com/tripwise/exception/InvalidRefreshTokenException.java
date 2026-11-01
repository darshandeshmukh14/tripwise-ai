package com.tripwise.exception;

/**
 * Refresh token is unknown, expired, revoked or reused. Same message for all cases.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Your session has expired. Please log in again.");
    }
}
