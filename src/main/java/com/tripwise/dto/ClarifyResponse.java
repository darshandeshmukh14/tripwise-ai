package com.tripwise.dto;

import java.util.List;

/**
 * @param type SINGLE_CHOICE, MULTI_CHOICE, TEXT or NUMBER
 */
public record ClarifyResponse(List<Question> questions) {

    public record Question(String id, String text, String type, List<String> options) {
    }
}
