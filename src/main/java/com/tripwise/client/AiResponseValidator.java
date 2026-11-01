package com.tripwise.client;

import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.client.dto.AiItineraryResponse.CostItem;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.exception.AiServiceException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rejects AI responses that are structurally invalid or whose numbers don't add up, so nothing bad is persisted.
 */
final class AiResponseValidator {

    // Per-line rounding on the Python side may leave a few units of drift
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("5.00");
    private static final int MAX_QUESTIONS = 10;
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_STOPS = 6;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_URL_LENGTH = 2000;
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private AiResponseValidator() {
    }

    static void validateItinerary(AiItineraryResponse response) {
        if (response == null) {
            fail("empty body");
        }
        if (response.days() == null || !response.days().isArray() || response.days().isEmpty()) {
            fail("no itinerary days");
        }
        if (response.costBreakdown() == null || response.costBreakdown().isEmpty()) {
            fail("no cost breakdown");
        }
        if (response.currency() != null && !CURRENCY.matcher(response.currency()).matches()) {
            fail("invalid currency");
        }
        BigDecimal total = response.total();
        if (total == null || total.signum() < 0) {
            fail("missing or negative total cost");
        }
        if (response.fx() != null && (response.fx().rate() == null || response.fx().rate().signum() <= 0)) {
            fail("invalid exchange rate");
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (CostItem item : response.costBreakdown()) {
            if (item == null || item.category() == null || item.label() == null || item.label().isBlank()
                    || item.season() == null || item.confidence() == null) {
                fail("incomplete cost item");
            }
            validateRange(item.amount());
            validateOptionalUrl(item.sourceUrl());
            if ((item.stopSeq() != null && (item.stopSeq() < 1 || item.stopSeq() > MAX_STOPS))
                    || (item.legIndex() != null && (item.legIndex() < 0 || item.legIndex() > MAX_STOPS))) {
                fail("cost item references an invalid stop or leg");
            }
            sum = sum.add(item.amount().expected());
        }
        if (sum.subtract(total).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            fail("cost items do not add up to the total");
        }
        if (response.bookingLinks() != null) {
            for (AiItineraryResponse.BookingLink link : response.bookingLinks()) {
                if (link == null || isBlank(link.label()) || !isWebUrl(link.url())) {
                    fail("invalid booking link");
                }
            }
        }
        if (response.webHints() != null) {
            for (AiItineraryResponse.WebHint hint : response.webHints()) {
                if (hint == null || !isWebUrl(hint.sourceUrl())) {
                    fail("invalid web hint");
                }
            }
        }
    }

    static void validateRoute(AiRouteResponse response, int expectedStops) {
        if (response == null || response.stops() == null || response.legs() == null) {
            fail("empty route body");
        }
        if (response.stops().size() != expectedStops || expectedStops > MAX_STOPS) {
            fail("unexpected number of stops");
        }
        for (AiRouteResponse.Stop stop : response.stops()) {
            if (stop == null || isBlank(stop.city()) || stop.city().length() > 255 || stop.nights() == null
                    || stop.nights() < 1 || stop.countryCode() == null
                    || !stop.countryCode().matches("^[A-Za-z]{2}$")) {
                fail("incomplete route stop");
            }
        }
    }

    static void validateSuggestions(AiSuggestResponse response) {
        if (response == null || response.suggestions() == null) {
            fail("empty suggestions body");
        }
        if (response.suggestions().size() > MAX_SUGGESTIONS) {
            fail("too many suggestions");
        }
        for (AiSuggestResponse.Suggestion s : response.suggestions()) {
            if (s == null || isBlank(s.city()) || s.suggestedNights() == null || s.suggestedNights() < 1
                    || s.currency() == null || !CURRENCY.matcher(s.currency()).matches()) {
                fail("incomplete suggestion");
            }
            validateRange(s.extraCost());
        }
    }

    private static void validateOptionalUrl(String url) {
        if (url != null && !isWebUrl(url)) {
            fail("invalid source URL");
        }
    }

    /**
     * Only absolute http(s) URLs: anything else (javascript:, data:, relative) must never reach the browser.
     */
    static boolean isWebUrl(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    static void validateClarify(AiClarifyResponse response) {
        if (response == null || response.questions() == null) {
            fail("empty clarify body");
        }
        if (response.questions().size() > MAX_QUESTIONS) {
            fail("too many clarifying questions");
        }
        for (AiClarifyResponse.Question q : response.questions()) {
            if (q == null || isBlank(q.id()) || isBlank(q.text()) || isBlank(q.type())) {
                fail("incomplete clarifying question");
            }
        }
    }

    static void validateRefine(AiRefineResponse response) {
        if (response == null || isBlank(response.assistantMessage())) {
            fail("empty refine body");
        }
        if (response.assistantMessage().length() > MAX_MESSAGE_LENGTH) {
            fail("assistant message too long");
        }
        if (response.updatedPlan() != null) {
            validateItinerary(response.updatedPlan());
        }
    }

    private static void validateRange(AmountRange range) {
        if (range == null || range.min() == null || range.expected() == null || range.max() == null) {
            fail("incomplete amount range");
        }
        if (range.min().signum() < 0 || range.min().compareTo(range.expected()) > 0
                || range.expected().compareTo(range.max()) > 0) {
            fail("invalid amount range");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void fail(String reason) {
        throw new AiServiceException("AI service response invalid: " + reason);
    }
}
