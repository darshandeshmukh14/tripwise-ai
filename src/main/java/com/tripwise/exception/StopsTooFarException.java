package com.tripwise.exception;

import lombok.Getter;

import java.util.List;

/**
 * Multi-city stops that break the route rules (unknown place, unsupported country or a leg over 400 km by road).
 */
@Getter
public class StopsTooFarException extends RuntimeException {

    private final List<String> violations;

    public StopsTooFarException(List<String> violations) {
        super("Some stops can't be combined in one trip.");
        this.violations = List.copyOf(violations);
    }
}
