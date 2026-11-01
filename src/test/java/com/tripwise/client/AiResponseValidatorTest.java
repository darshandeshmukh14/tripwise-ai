package com.tripwise.client;

import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.client.dto.AiItineraryResponse.CostItem;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.entity.Confidence;
import com.tripwise.entity.CostCategory;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Season;
import com.tripwise.exception.AiServiceException;
import com.tripwise.testsupport.AiResponses;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseValidatorTest {

    @Test
    void validateItinerary_consistentResponse_passes() {
        assertThatCode(() -> AiResponseValidator.validateItinerary(AiResponses.itinerary("10000")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateItinerary_sumWithinTolerance_passes() {
        AiItineraryResponse response = withTotal(AiResponses.itinerary("10000"), "10004.99");

        assertThatCode(() -> AiResponseValidator.validateItinerary(response)).doesNotThrowAnyException();
    }

    @Test
    void validateItinerary_sumMismatch_fails() {
        assertInvalid(withTotal(AiResponses.itinerary("10000"), "10005.01"), "add up");
    }

    @Test
    void validateItinerary_emptyBreakdown_fails() {
        AiItineraryResponse r = AiResponses.itinerary("100");
        assertInvalid(copy(r, List.of()), "cost breakdown");
    }

    @Test
    void validateItinerary_invertedRange_fails() {
        CostItem bad = new CostItem(CostCategory.FOOD, null, "Food",
                new AmountRange(new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("300")),
                Season.OFF, CostSource.ESTIMATE, Confidence.LOW, null, null, null, null);
        assertInvalid(withTotal(copy(AiResponses.itinerary("100"), List.of(bad)), "100"), "range");
    }

    @Test
    void validateItinerary_missingConfidence_fails() {
        CostItem bad = new CostItem(CostCategory.FOOD, null, "Food",
                new AmountRange(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                Season.OFF, CostSource.ESTIMATE, null, null, null, null, null);
        assertInvalid(withTotal(copy(AiResponses.itinerary("1"), List.of(bad)), "1"), "incomplete cost item");
    }

    @Test
    void validateItinerary_foreignCurrency_checksTotalEstimatedCost() {
        AiItineraryResponse r = AiResponses.itinerary("100");
        AiItineraryResponse usd = withExtras(r, "USD", new BigDecimal("100"), r.costBreakdown(), null);

        assertThatCode(() -> AiResponseValidator.validateItinerary(usd)).doesNotThrowAnyException();
        assertInvalid(withExtras(r, "USD", new BigDecimal("50"), r.costBreakdown(), null), "add up");
        assertInvalid(withExtras(r, "usd", new BigDecimal("100"), r.costBreakdown(), null), "currency");
    }

    @Test
    void validateItinerary_javascriptSourceUrl_fails() {
        CostItem bad = new CostItem(CostCategory.FOOD, null, "Food",
                new AmountRange(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                Season.OFF, CostSource.WEB_HINT, Confidence.LOW, null, null, null, "javascript:alert(1)");
        assertInvalid(withTotal(copy(AiResponses.itinerary("1"), List.of(bad)), "1"), "source URL");
    }

    @Test
    void validateItinerary_invalidStopSeq_fails() {
        CostItem bad = new CostItem(CostCategory.STAY, null, "Hotel",
                new AmountRange(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                Season.OFF, CostSource.ESTIMATE, Confidence.LOW, null, 7, null, null);
        assertInvalid(withTotal(copy(AiResponses.itinerary("1"), List.of(bad)), "1"), "stop or leg");
    }

    @Test
    void validateItinerary_bookingLinkWithDataUrl_fails() {
        AiItineraryResponse r = AiResponses.itinerary("100");
        List<AiItineraryResponse.BookingLink> links = List.of(new AiItineraryResponse.BookingLink(
                "x", "Book", "data:text/html,hi", "STAY", null, 1, false));

        assertInvalid(withExtras(r, null, null, r.costBreakdown(), links), "booking link");
    }

    @Test
    void isWebUrl_acceptsOnlyAbsoluteHttp() {
        assertThat(AiResponseValidator.isWebUrl("https://www.irctc.co.in/")).isTrue();
        assertThat(AiResponseValidator.isWebUrl("/relative")).isFalse();
        assertThat(AiResponseValidator.isWebUrl("JavaScript:alert(1)")).isFalse();
    }

    @Test
    void validateRoute_wrongStopCount_fails() {
        AiRouteResponse route = new AiRouteResponse(
                List.of(new AiRouteResponse.Stop(1, "Delhi", "IN", 2, true)), List.of(), true, List.of());

        assertThatThrownBy(() -> AiResponseValidator.validateRoute(route, 2)).isInstanceOf(AiServiceException.class);
        assertThatCode(() -> AiResponseValidator.validateRoute(route, 1)).doesNotThrowAnyException();
    }

    @Test
    void validateSuggestions_blankCity_fails() {
        AiSuggestResponse response = new AiSuggestResponse(List.of(new AiSuggestResponse.Suggestion(
                " ", "IN", "Delhi", BigDecimal.TEN, BigDecimal.ONE, 1,
                new AmountRange(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), "INR", Season.OFF, "r")));

        assertThatThrownBy(() -> AiResponseValidator.validateSuggestions(response))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validateClarify_tooManyQuestions_fails() {
        List<AiClarifyResponse.Question> questions = Collections.nCopies(11,
                new AiClarifyResponse.Question("q", "Q?", "text", null));

        assertThatThrownBy(() -> AiResponseValidator.validateClarify(new AiClarifyResponse(questions)))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validateRefine_blankMessage_fails() {
        assertThatThrownBy(() -> AiResponseValidator.validateRefine(new AiRefineResponse(" ", null)))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void validateRefine_invalidUpdatedPlan_fails() {
        AiItineraryResponse badPlan = withTotal(AiResponses.itinerary("100"), "999");

        assertThatThrownBy(() -> AiResponseValidator.validateRefine(new AiRefineResponse("ok", badPlan)))
                .isInstanceOf(AiServiceException.class);
    }

    private static void assertInvalid(AiItineraryResponse response, String reason) {
        assertThatThrownBy(() -> AiResponseValidator.validateItinerary(response))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining(reason);
    }

    private static AiItineraryResponse withTotal(AiItineraryResponse r, String total) {
        return new AiItineraryResponse(r.tripTitle(), r.destination(), r.origin(), r.comfortLevel(),
                new BigDecimal(total), r.totalCost(), r.budgetStatus(), r.season(), r.transportOptions(), r.stay(),
                r.costBreakdown(), r.warnings(), r.days(), r.currency(), r.totalEstimatedCost(), r.fx(), r.stops(),
                r.legs(), r.bookingLinks(), r.webHints(), r.webHintsSkipped());
    }

    private static AiItineraryResponse copy(AiItineraryResponse r, List<CostItem> items) {
        return withExtras(r, r.currency(), r.totalEstimatedCost(), items, r.bookingLinks());
    }

    private static AiItineraryResponse withExtras(AiItineraryResponse r, String currency, BigDecimal total,
                                                  List<CostItem> items,
                                                  List<AiItineraryResponse.BookingLink> links) {
        return new AiItineraryResponse(r.tripTitle(), r.destination(), r.origin(), r.comfortLevel(),
                r.totalEstimatedCostInr(), r.totalCost(), r.budgetStatus(), r.season(), r.transportOptions(),
                r.stay(), items, r.warnings(), r.days(), currency, total, r.fx(), r.stops(), r.legs(), links,
                r.webHints(), r.webHintsSkipped());
    }
}
