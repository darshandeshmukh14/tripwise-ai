package com.tripwise.client;

import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.client.dto.AiRouteRequest;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.client.dto.AiSuggestRequest;
import com.tripwise.client.dto.AiSuggestResponse;
import com.tripwise.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The only class that talks HTTP to the internal Python AI service.
 * Every failure (non-2xx, timeout, I/O, malformed or invalid body) surfaces as {@link AiServiceException}.
 */
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    static final String GENERATE_ITINERARY_PATH = "/internal/v1/itineraries/generate";
    static final String REFINE_ITINERARY_PATH = "/internal/v1/itineraries/refine";
    static final String CLARIFY_PATH = "/internal/v1/preferences/clarify";
    static final String ROUTE_PATH = "/internal/v1/stops/route";
    static final String SUGGEST_STOPS_PATH = "/internal/v1/stops/suggest";

    private final RestClient aiServiceRestClient;

    /**
     * Resolves multi-city stops to canonical names and checks the 400 km leg rule (optionally reordering them).
     */
    public AiRouteResponse checkRoute(AiRouteRequest request) {
        AiRouteResponse response = post(ROUTE_PATH, request, AiRouteResponse.class);
        AiResponseValidator.validateRoute(response, request.stops().size());
        return response;
    }

    public AiSuggestResponse suggestStops(AiSuggestRequest request) {
        AiSuggestResponse response = post(SUGGEST_STOPS_PATH, request, AiSuggestResponse.class);
        AiResponseValidator.validateSuggestions(response);
        return response;
    }

    public AiItineraryResponse generateItinerary(AiGenerateTripRequest request) {
        AiItineraryResponse response = post(GENERATE_ITINERARY_PATH, request, AiItineraryResponse.class);
        AiResponseValidator.validateItinerary(response);
        return response;
    }

    public AiClarifyResponse clarify(AiGenerateTripRequest request) {
        AiClarifyResponse response = post(CLARIFY_PATH, request, AiClarifyResponse.class);
        AiResponseValidator.validateClarify(response);
        return response;
    }

    public AiRefineResponse refine(AiRefineRequest request) {
        AiRefineResponse response = post(REFINE_ITINERARY_PATH, request, AiRefineResponse.class);
        AiResponseValidator.validateRefine(response);
        return response;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return aiServiceRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new AiServiceException("AI service returned HTTP " + res.getStatusCode().value());
                    })
                    .body(responseType);
        } catch (AiServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new AiServiceException("AI service call failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
