package com.tripwise.exception;

/**
 * Thrown when a resource does not exist or is not owned by the current user (both map to 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
