package com.tripwise.service;

import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiRouteRequest;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestRequest;
import com.tripwise.dto.StopSuggestionsResponse;
import com.tripwise.dto.TripInput;
import com.tripwise.exception.AiServiceException;
import com.tripwise.exception.ResourceNotFoundException;
import com.tripwise.exception.StopsTooFarException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.TripRepository;
import com.tripwise.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Multi-city stops: route validation (max 400 km by road between stops) and "add a nearby place" suggestions.
 * Both call the AI service, so they must run outside a database transaction.
 */
@Service
@RequiredArgsConstructor
public class TripStopService {

    static final int SUGGESTION_LIMIT = 5;

    private final AiServiceClient aiServiceClient;
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;
    private final TransactionTemplate transactionTemplate;
    private final CurrentUserProvider currentUserProvider;

    /**
     * @return canonical stops in visiting order (possibly reordered when requested); empty for a single destination
     * @throws StopsTooFarException if a stop is unknown, unsupported or more than 400 km by road from the previous
     * @throws AiServiceException   if the AI service is unavailable
     */
    public List<AiRouteResponse.Stop> resolve(TripInput input) {
        if (input.stops() == null || input.stops().isEmpty()) {
            return List.of();
        }
        String origin = input.origin() == null || input.origin().isBlank() ? null : input.origin().trim();
        AiRouteResponse route = aiServiceClient.checkRoute(new AiRouteRequest(origin,
                tripMapper.toAiStops(input.stops()), Boolean.TRUE.equals(input.optimizeRouteOrder())));
        if (!route.valid()) {
            throw new StopsTooFarException(route.violations() == null ? List.of() : route.violations());
        }
        return route.stops();
    }

    /**
     * Nearby places (within 400 km of a stop) worth adding to the current user's trip.
     *
     * @throws ResourceNotFoundException if the trip is missing or not owned by the current user
     */
    public StopSuggestionsResponse suggest(Long tripId) {
        Long userId = currentUserProvider.getCurrentUserId();
        AiGenerateTripRequest trip = transactionTemplate.execute(status -> tripRepository
                .findByIdAndUserId(tripId, userId)
                .map(tripMapper::toAiRequest)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found.")));
        if (trip.stops().size() >= TripInput.MAX_STOPS) {
            return new StopSuggestionsResponse(List.of());
        }
        return tripMapper.toSuggestions(aiServiceClient.suggestStops(new AiSuggestRequest(trip, SUGGESTION_LIMIT)));
    }
}
