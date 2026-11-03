package com.tripwise.service;

import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiClarifyResponse;
import com.tripwise.client.dto.AiGenerateTripRequest;
import com.tripwise.dto.ClarifyResponse;
import com.tripwise.dto.TripStatusResponse;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.AiServiceException;
import com.tripwise.exception.GenerationInProgressException;
import com.tripwise.exception.ResourceNotFoundException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.TripRepository;
import com.tripwise.security.CurrentUserProvider;
import com.tripwise.security.WebHintLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripGenerationService {

    static final String BUDGET_ANSWER = "budget";
    // Matches the trips.budget_amount NUMERIC(12,2) column
    private static final BigDecimal MAX_BUDGET = new BigDecimal("9999999999.99");

    private final TripRepository tripRepository;
    private final TripGenerationWorker tripGenerationWorker;
    private final TransactionTemplate transactionTemplate;
    private final TripMapper tripMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AiServiceClient aiServiceClient;
    private final WebHintLimiter webHintLimiter;

    /**
     * Marks the current user's trip as GENERATING and schedules the AI call in the background.
     * The status change is committed before the async task starts, so the worker never races it.
     *
     * @param answers answers to clarifying questions (may be empty)
     * @throws ResourceNotFoundException     if the trip is missing or not owned by the current user
     * @throws GenerationInProgressException if generation is already running for the trip
     * @throws AiServiceException            if the generation queue is full
     */
    public TripStatusResponse triggerGeneration(Long tripId, Map<String, String> answers) {
        Long userId = currentUserProvider.getCurrentUserId();
        AiGenerateTripRequest request = transactionTemplate.execute(status -> startGeneration(tripId, userId, answers));

        try {
            tripGenerationWorker.generate(tripId, userId, request);
        } catch (TaskRejectedException e) {
            log.error("Generation queue full; trip id={} marked FAILED", tripId);
            tripGenerationWorker.markFailed(tripId, userId);
            throw new AiServiceException("Itinerary generation queue is full", e);
        }

        log.info("Itinerary generation scheduled for trip id={}", tripId);
        return new TripStatusResponse(tripId, TripStatus.GENERATING);
    }

    /**
     * Asks the AI service which inputs are missing or conflicting. Read-only; the AI call runs outside a transaction.
     */
    public ClarifyResponse clarify(Long tripId) {
        Long userId = currentUserProvider.getCurrentUserId();
        AiGenerateTripRequest request = transactionTemplate.execute(status -> tripRepository
                .findByIdAndUserId(tripId, userId)
                .map(tripMapper::toAiRequest)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found.")));

        return toClarifyResponse(aiServiceClient.clarify(request));
    }

    static ClarifyResponse toClarifyResponse(AiClarifyResponse response) {
        return new ClarifyResponse(response.questions().stream()
                .map(q -> new ClarifyResponse.Question(q.id(), q.text(), q.type(),
                        q.options() == null ? List.of() : List.copyOf(q.options())))
                .toList());
    }

    private AiGenerateTripRequest startGeneration(Long tripId, Long userId, Map<String, String> answers) {
        int updated = tripRepository.updateStatusIfDifferent(tripId, userId, TripStatus.GENERATING);
        if (updated == 0) {
            if (!tripRepository.existsByIdAndUserId(tripId, userId)) {
                throw new ResourceNotFoundException("Trip not found.");
            }
            throw new GenerationInProgressException();
        }
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found."));
        applyAnswers(trip, answers);
        return tripMapper.toAiRequest(trip).withAllowWebHints(webHintLimiter.tryConsume(userId));
    }

    /**
     * Merges new answers into the stored ones (new values win). A valid "budget" answer sets the trip budget
     * instead of being stored, because the AI service only reads {@code budgetAmount}.
     */
    static void applyAnswers(Trip trip, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        Map<String, String> merged = new LinkedHashMap<>(trip.getPlanningAnswers());
        answers.forEach((key, value) -> {
            if (BUDGET_ANSWER.equals(key)) {
                parseBudget(value).ifPresent(trip::setBudgetAmount);
            } else {
                merged.put(key, value.trim());
            }
        });
        trip.setPlanningAnswers(merged);
    }

    private static Optional<BigDecimal> parseBudget(String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP);
            return amount.signum() > 0 && amount.compareTo(MAX_BUDGET) <= 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }
}
