package com.tripwise.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.tripwise.entity.BudgetStatus;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.Confidence;
import com.tripwise.entity.CostCategory;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Season;
import com.tripwise.entity.TransportMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Mirrors the AI service's {@code ItineraryResponse} Pydantic model (camelCase). Amounts are estimates in
 * {@code currency} ({@code totalEstimatedCostInr} is the INR equivalent). {@code days}, {@code stops} and
 * {@code legs} stay raw JSON because their structure is owned by the AI service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiItineraryResponse(
        String tripTitle,
        String destination,
        String origin,
        ComfortLevel comfortLevel,
        BigDecimal totalEstimatedCostInr,
        AmountRange totalCost,
        BudgetStatus budgetStatus,
        SeasonInfo season,
        List<TransportOption> transportOptions,
        Stay stay,
        List<CostItem> costBreakdown,
        List<String> warnings,
        JsonNode days,
        String currency,
        BigDecimal totalEstimatedCost,
        Fx fx,
        JsonNode stops,
        JsonNode legs,
        List<BookingLink> bookingLinks,
        List<WebHint> webHints,
        Boolean webHintsSkipped
) {

    /**
     * @return the total in the itinerary currency (plans from before v2 only carry the INR total)
     */
    public BigDecimal total() {
        return totalEstimatedCost != null ? totalEstimatedCost : totalEstimatedCostInr;
    }

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "INR" : currency;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmountRange(BigDecimal min, BigDecimal expected, BigDecimal max) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonInfo(Season type, BigDecimal multiplier, String notes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransportOption(
            TransportMode mode,
            String travelClass,
            BigDecimal distanceKm,
            BigDecimal durationHours,
            AmountRange cost,
            Integer comfortScore,
            boolean recommended,
            String notes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stay(ComfortLevel tier, AmountRange nightlyRate, Integer nights, Integer rooms,
                       String areaSuggestion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostItem(
            CostCategory category,
            TransportMode mode,
            String label,
            AmountRange amount,
            Season season,
            CostSource source,
            Confidence confidence,
            String assumptions,
            Integer stopSeq,
            Integer legIndex,
            String sourceUrl
    ) {
    }

    /**
     * @param rate units of {@code quote} per 1 {@code base} (INR)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fx(String base, String quote, BigDecimal rate, Instant asOf, String source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingLink(String provider, String label, String url, String kind, Integer legIndex,
                              Integer stopSeq, boolean sponsored) {
    }

    /**
     * @param searchSuggestionsHtml Google Search Suggestions markup that must be shown with grounded results
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebHint(String label, String sourceUrl, String sourceTitle, String searchSuggestionsHtml) {
    }
}
