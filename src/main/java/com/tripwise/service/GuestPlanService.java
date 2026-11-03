package com.tripwise.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.AiResponseValidator;
import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestRequest;
import com.tripwise.dto.ClaimTripRequest;
import com.tripwise.dto.ClarifyResponse;
import com.tripwise.dto.CreateTripRequest;
import com.tripwise.dto.GuestChatReplyResponse;
import com.tripwise.dto.GuestChatRequest;
import com.tripwise.dto.GuestTripRequest;
import com.tripwise.dto.StopSuggestionsResponse;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.dto.TripInput;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.AiServiceException;
import com.tripwise.exception.InvalidPlanException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.TripRepository;
import com.tripwise.repository.UserRepository;
import com.tripwise.security.CurrentUserProvider;
import com.tripwise.security.WebHintLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Planning without an account: trips and plans live only in the browser and are sent back with each request, so
 * nothing is stored and no ownership applies. Web price hints are budgeted per client IP. A guest plan can later
 * be saved to an account with {@link #claim}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestPlanService {

    static final int MAX_PLAN_CHARS = 200_000;
    private static final int GUEST_VERSION = 1;

    private final AiServiceClient aiServiceClient;
    private final TripStopService tripStopService;
    private final TripMapper tripMapper;
    private final ItineraryWriter itineraryWriter;
    private final ObjectMapper objectMapper;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final TripService tripService;
    private final WebHintLimiter webHintLimiter;

    public ClarifyResponse clarify(GuestTripRequest request) {
        Trip trip = buildTrip(request.trip(), request.answers());
        return TripGenerationService.toClarifyResponse(aiServiceClient.clarify(tripMapper.toAiRequest(trip)));
    }

    public StopSuggestionsResponse suggestStops(GuestTripRequest request) {
        Trip trip = buildTrip(request.trip(), request.answers());
        if (trip.getStops().size() >= TripInput.MAX_STOPS) {
            return new StopSuggestionsResponse(List.of());
        }
        return tripMapper.toSuggestions(aiServiceClient.suggestStops(
                new AiSuggestRequest(tripMapper.toAiRequest(trip), TripStopService.SUGGESTION_LIMIT)));
    }

    /**
     * Generates synchronously (no background job: there is no stored trip to poll).
     */
    public TripDetailResponse generate(GuestTripRequest request, String clientIp) {
        Trip trip = buildTrip(request.trip(), request.answers());
        return toDetail(trip, aiServiceClient.generateItinerary(aiRequest(trip, clientIp)));
    }

    /**
     * @throws InvalidPlanException if the plan sent by the browser is not a valid itinerary
     */
    public GuestChatReplyResponse refine(GuestChatRequest request, String clientIp) {
        AiItineraryResponse current = parsePlan(request.plan());
        Trip trip = buildTrip(request.trip(), request.answers());
        List<AiRefineRequest.Turn> history = request.history() == null ? List.of() : request.history().stream()
                .map(t -> new AiRefineRequest.Turn(t.role(), t.content()))
                .toList();

        AiRefineResponse reply = aiServiceClient.refine(new AiRefineRequest(null, aiRequest(trip, clientIp),
                objectMapper.valueToTree(current), history, request.content().trim()));
        boolean updated = reply.updatedPlan() != null;
        return new GuestChatReplyResponse(reply.assistantMessage(), updated,
                toDetail(trip, updated ? reply.updatedPlan() : current));
    }

    /**
     * Saves a guest trip and its plan to the current user's account.
     *
     * @throws InvalidPlanException if the plan is not a valid itinerary
     */
    public TripDetailResponse claim(ClaimTripRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        AiItineraryResponse plan = parsePlan(request.plan());
        List<AiRouteResponse.Stop> stops = tripStopService.resolve(request.trip());
        Long tripId = transactionTemplate.execute(status -> {
            Trip trip = tripMapper.toEntity(request.trip());
            trip.setUser(userRepository.getReferenceById(userId));
            trip.setStatus(TripStatus.DRAFT);
            setStops(trip, stops);
            TripGenerationService.applyAnswers(trip, request.answers());
            Trip saved = tripRepository.save(trip);
            itineraryWriter.saveNewVersion(saved, userId, plan);
            return saved.getId();
        });
        log.info("Saved guest plan as trip id={} for user id={}", tripId, userId);
        return tripService.getTripById(tripId);
    }

    private AiGenerateTripRequest aiRequest(Trip trip, String clientIp) {
        return tripMapper.toAiRequest(trip).withAllowWebHints(webHintLimiter.tryConsumeGuest(clientIp));
    }

    private Trip buildTrip(CreateTripRequest input, Map<String, String> answers) {
        List<AiRouteResponse.Stop> stops = tripStopService.resolve(input);
        Trip trip = tripMapper.toEntity(input);
        setStops(trip, stops);
        TripGenerationService.applyAnswers(trip, answers);
        return trip;
    }

    private void setStops(Trip trip, List<AiRouteResponse.Stop> stops) {
        if (!stops.isEmpty()) {
            trip.getStops().addAll(tripMapper.toStops(trip, stops));
            trip.setDestination(stops.get(0).city().trim());
        }
    }

    private TripDetailResponse toDetail(Trip trip, AiItineraryResponse plan) {
        trip.setStatus(TripStatus.COMPLETED);
        TripItinerary itinerary = new TripItinerary();
        itinerary.setVersion(GUEST_VERSION);
        itineraryWriter.applyPlan(itinerary, plan);
        return tripMapper.toDetail(trip, itinerary, itineraryWriter.toCostItems(trip, GUEST_VERSION, plan));
    }

    /**
     * Plans come back from the browser, so they get the same checks as AI responses (web hint links must be
     * http(s); their search-suggestion HTML is only ever shown in a sandboxed iframe).
     */
    private AiItineraryResponse parsePlan(JsonNode node) {
        if (node == null || !node.isObject() || node.toString().length() > MAX_PLAN_CHARS) {
            throw new InvalidPlanException();
        }
        try {
            AiItineraryResponse plan = objectMapper.treeToValue(node, AiItineraryResponse.class);
            AiResponseValidator.validateItinerary(plan);
            return plan;
        } catch (JsonProcessingException | IllegalArgumentException | AiServiceException e) {
            throw new InvalidPlanException();
        }
    }
}
