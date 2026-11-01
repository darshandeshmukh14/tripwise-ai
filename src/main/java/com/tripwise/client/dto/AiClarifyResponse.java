package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of {@code POST /internal/v1/preferences/clarify}: questions only for missing or conflicting inputs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiClarifyResponse(List<Question> questions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String id, String text, String type, List<String> options) {
    }
}
