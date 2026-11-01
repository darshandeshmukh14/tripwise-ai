package com.tripwise.client.dto;

/**
 * One multi-city stop as sent to the AI service.
 */
public record AiStop(String city, String countryCode, Integer nights) {
}
