package com.tripwise.mapper;

import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiStop;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.dto.CostBreakdownResponse;
import com.tripwise.dto.ItineraryResponse;
import com.tripwise.dto.StopSuggestionsResponse;
import com.tripwise.dto.SupportedCurrencies;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.dto.TripInput;
import com.tripwise.dto.TripStopRequest;
import com.tripwise.dto.TripStopResponse;
import com.tripwise.dto.TripSummaryResponse;
import com.tripwise.entity.BudgetStatus;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripCostItem;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStop;
import com.tripwise.service.BudgetStatusCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TripMapper {

    public Trip toEntity(TripInput input) {
        Trip trip = new Trip();
        apply(input, trip);
        return trip;
    }

    /**
     * Copies all editable fields (PUT semantics: omitted optional fields are cleared or defaulted).
     */
    public void apply(TripInput input, Trip trip) {
        trip.setDestination(input.destination().trim());
        trip.setOrigin(input.origin() == null ? null : input.origin().trim());
        trip.setStartDate(input.startDate());
        trip.setEndDate(input.endDate());
        trip.setTravellersCount(input.travellersCount());
        trip.setBudgetAmount(input.budgetAmount());
        trip.setCurrency(SupportedCurrencies.normalize(input.currency()));
        trip.setTravelStyle(input.travelStyle() == null || input.travelStyle().isBlank()
                ? null : input.travelStyle().trim());
        trip.setComfortLevel(input.comfortLevel() == null ? ComfortLevel.STANDARD : input.comfortLevel());
        trip.setTransportModes(input.transportModes() == null
                ? new ArrayList<>()
                : new ArrayList<>(input.transportModes().stream().distinct().toList()));
        trip.setPreferences(input.preferences() == null
                ? new ArrayList<>()
                : new ArrayList<>(input.preferences().stream().map(String::trim).toList()));
    }

    public TripSummaryResponse toSummary(Trip trip) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getStatus(),
                trip.getBudgetAmount(),
                trip.getCurrency());
    }

    /**
     * Builds the stop entities for a trip from the AI service's canonical (and possibly reordered) route.
     */
    public List<TripStop> toStops(Trip trip, List<AiRouteResponse.Stop> resolved) {
        List<TripStop> stops = new ArrayList<>(resolved.size());
        for (int i = 0; i < resolved.size(); i++) {
            AiRouteResponse.Stop source = resolved.get(i);
            TripStop stop = new TripStop();
            stop.setTrip(trip);
            stop.setSeq(i + 1);
            stop.setCity(source.city().trim());
            stop.setCountryCode(source.countryCode().toUpperCase(Locale.ROOT));
            stop.setNights(source.nights());
            stops.add(stop);
        }
        return stops;
    }

    public List<AiStop> toAiStops(List<TripStopRequest> stops) {
        return stops == null ? List.of() : stops.stream()
                .map(s -> new AiStop(s.city().trim(),
                        s.countryCode() == null ? null : s.countryCode().toUpperCase(Locale.ROOT), s.nights()))
                .toList();
    }

    public StopSuggestionsResponse toSuggestions(AiSuggestResponse response) {
        return new StopSuggestionsResponse(response.suggestions().stream()
                .map(s -> new StopSuggestionsResponse.Suggestion(s.city(), s.countryCode(), s.nearStop(),
                        s.distanceKm(), s.travelHours(), s.suggestedNights(), s.extraCost().min(),
                        s.extraCost().expected(), s.extraCost().max(), s.currency(), s.season(), s.reason()))
                .toList());
    }

    public TripDetailResponse toDetail(Trip trip, TripItinerary itinerary, List<TripCostItem> costItems) {
        return new TripDetailResponse(
                trip.getId(),
                trip.getDestination(),
                trip.getOrigin(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getTravellersCount(),
                trip.getBudgetAmount(),
                trip.getCurrency(),
                trip.getTravelStyle(),
                trip.getComfortLevel(),
                List.copyOf(trip.getTransportModes()),
                trip.getStatus(),
                List.copyOf(trip.getPreferences()),
                trip.getStops().stream()
                        .map(s -> new TripStopResponse(s.getSeq(), s.getCity(), s.getCountryCode(), s.getNights()))
                        .toList(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                itinerary == null ? null : toItinerary(trip, itinerary, costItems));
    }

    public AiGenerateTripRequest toAiRequest(Trip trip) {
        Map<String, String> answers = trip.getPlanningAnswers();
        return new AiGenerateTripRequest(
                trip.getId(),
                trip.getOrigin(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getTravellersCount(),
                trip.getBudgetAmount(),
                trip.getCurrency(),
                trip.getTravelStyle(),
                trip.getComfortLevel(),
                List.copyOf(trip.getTransportModes()),
                List.copyOf(trip.getPreferences()),
                answers == null ? Map.of() : Map.copyOf(answers),
                trip.getStops().stream()
                        .map(s -> new AiStop(s.getCity(), s.getCountryCode(), s.getNights()))
                        .toList(),
                false);
    }

    private ItineraryResponse toItinerary(Trip trip, TripItinerary itinerary, List<TripCostItem> costItems) {
        return new ItineraryResponse(
                itinerary.getId(),
                itinerary.getVersion(),
                itinerary.getTotalEstimatedCost(),
                itinerary.getCurrency(),
                itinerary.getFxRate(),
                itinerary.getFxAsOf(),
                itinerary.getPlanData(),
                costItems == null || costItems.isEmpty() ? null : toCostBreakdown(trip, itinerary, costItems),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt());
    }

    private CostBreakdownResponse toCostBreakdown(Trip trip, TripItinerary itinerary, List<TripCostItem> items) {
        BigDecimal min = BigDecimal.ZERO;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        List<CostBreakdownResponse.Item> lines = new ArrayList<>(items.size());
        for (TripCostItem item : items) {
            min = min.add(item.getAmountMin());
            expected = expected.add(item.getAmountExpected());
            max = max.add(item.getAmountMax());
            lines.add(new CostBreakdownResponse.Item(
                    item.getCategory(), item.getMode(), item.getLabel(),
                    item.getAmountMin(), item.getAmountExpected(), item.getAmountMax(),
                    item.getSeason(), item.getSource(), item.getConfidence(), item.getAssumptions(),
                    item.getStopSeq(), item.getLegIndex(), item.getSourceUrl()));
        }
        // A budget in another currency than the plan (trip edited after generation) can't be compared
        String currency = itinerary.getCurrency();
        BudgetStatus status = currency != null && currency.equals(trip.getCurrency())
                ? BudgetStatusCalculator.of(trip.getBudgetAmount(), expected)
                : BudgetStatus.UNKNOWN;
        return new CostBreakdownResponse(min, expected, max, currency, status, lines);
    }
}
