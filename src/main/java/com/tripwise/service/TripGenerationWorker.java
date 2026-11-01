package com.tripwise.service;

import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.config.AsyncConfig;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.AiServiceException;
import com.tripwise.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Background part of itinerary generation. Kept separate from {@link TripGenerationService} so the
 * {@code @Async} proxy applies. The AI call runs outside any DB transaction; results are persisted in short ones.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripGenerationWorker {

    private final AiServiceClient aiServiceClient;
    private final TripRepository tripRepository;
    private final ItineraryWriter itineraryWriter;
    private final TransactionTemplate transactionTemplate;

    @Async(AsyncConfig.TRIP_GENERATION_EXECUTOR)
    public void generate(Long tripId, Long userId, AiGenerateTripRequest request) {
        long start = System.nanoTime();
        try {
            AiItineraryResponse response = aiServiceClient.generateItinerary(request);
            transactionTemplate.executeWithoutResult(status -> saveItinerary(tripId, userId, response));
            log.info("Itinerary generated for trip id={} in {} ms", tripId, elapsedMs(start));
        } catch (AiServiceException e) {
            log.error("Itinerary generation failed for trip id={} after {} ms: {}", tripId, elapsedMs(start),
                    e.getMessage());
            markFailed(tripId, userId);
        } catch (RuntimeException e) {
            log.error("Itinerary generation failed for trip id={} after {} ms", tripId, elapsedMs(start), e);
            markFailed(tripId, userId);
        }
    }

    /**
     * Moves a trip from GENERATING to FAILED. Never throws, so it is safe to call from error paths.
     */
    public void markFailed(Long tripId, Long userId) {
        try {
            transactionTemplate.executeWithoutResult(status -> tripRepository.findByIdAndUserId(tripId, userId)
                    .filter(trip -> trip.getStatus() == TripStatus.GENERATING)
                    .ifPresent(trip -> trip.setStatus(TripStatus.FAILED)));
        } catch (RuntimeException e) {
            log.error("Could not mark trip id={} as FAILED", tripId, e);
        }
    }

    private void saveItinerary(Long tripId, Long userId, AiItineraryResponse response) {
        tripRepository.findByIdAndUserId(tripId, userId).ifPresentOrElse(
                trip -> itineraryWriter.saveNewVersion(trip, userId, response),
                () -> log.warn("Trip id={} no longer exists; discarding generated itinerary", tripId));
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
