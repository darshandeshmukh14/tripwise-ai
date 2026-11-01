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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TripGenerationServiceTest {

    private static final long TRIP_ID = 10L;
    private static final long USER_ID = 1L;

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripGenerationWorker worker;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private WebHintLimiter webHintLimiter;

    private TripGenerationService service;

    @BeforeEach
    void setUp() {
        service = new TripGenerationService(tripRepository, worker, new TransactionTemplate(transactionManager),
                new TripMapper(), currentUserProvider, aiServiceClient, webHintLimiter);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void triggerGeneration_ownedTrip_marksGeneratingAndSchedulesWorker() {
        givenTransitionSucceeds();

        TripStatusResponse response = service.triggerGeneration(TRIP_ID, Map.of());

        assertThat(response).isEqualTo(new TripStatusResponse(TRIP_ID, TripStatus.GENERATING));
        verify(worker).generate(org.mockito.ArgumentMatchers.eq(TRIP_ID), org.mockito.ArgumentMatchers.eq(USER_ID),
                any(AiGenerateTripRequest.class));
    }

    @Test
    void triggerGeneration_notOwned_throwsNotFound() {
        given(tripRepository.updateStatusIfDifferent(TRIP_ID, USER_ID, TripStatus.GENERATING)).willReturn(0);
        given(tripRepository.existsByIdAndUserId(TRIP_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.triggerGeneration(TRIP_ID, Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(worker, never()).generate(any(), any(), any());
    }

    @Test
    void triggerGeneration_alreadyGenerating_throwsConflict() {
        given(tripRepository.updateStatusIfDifferent(TRIP_ID, USER_ID, TripStatus.GENERATING)).willReturn(0);
        given(tripRepository.existsByIdAndUserId(TRIP_ID, USER_ID)).willReturn(true);

        assertThatThrownBy(() -> service.triggerGeneration(TRIP_ID, Map.of()))
                .isInstanceOf(GenerationInProgressException.class);
        verify(worker, never()).generate(any(), any(), any());
    }

    @Test
    void triggerGeneration_queueFull_marksFailedAndThrows() {
        givenTransitionSucceeds();
        willThrow(new TaskRejectedException("full")).given(worker).generate(any(), any(), any());

        assertThatThrownBy(() -> service.triggerGeneration(TRIP_ID, Map.of()))
                .isInstanceOf(AiServiceException.class);
        verify(worker).markFailed(TRIP_ID, USER_ID);
    }

    @Test
    void triggerGeneration_withAnswers_passesAnswersToAiRequest() {
        givenTransitionSucceeds();

        service.triggerGeneration(TRIP_ID, Map.of("pace", "relaxed"));

        org.mockito.ArgumentCaptor<AiGenerateTripRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(worker).generate(org.mockito.ArgumentMatchers.eq(TRIP_ID), org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture());
        assertThat(captor.getValue().answers()).containsEntry("pace", "relaxed");
        assertThat(captor.getValue().allowWebHints()).isFalse();
    }

    @Test
    void triggerGeneration_webHintBudgetLeft_allowsWebHints() {
        givenTransitionSucceeds();
        given(webHintLimiter.tryConsume(USER_ID)).willReturn(true);

        service.triggerGeneration(TRIP_ID, Map.of());

        org.mockito.ArgumentCaptor<AiGenerateTripRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(worker).generate(org.mockito.ArgumentMatchers.eq(TRIP_ID), org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture());
        assertThat(captor.getValue().allowWebHints()).isTrue();
    }

    @Test
    void applyAnswers_mergesWithStoredAnswersAndAppliesBudget() {
        Trip trip = new Trip();
        trip.setPlanningAnswers(Map.of("pace", "packed", "interests", "Food"));

        TripGenerationService.applyAnswers(trip, Map.of("pace", " relaxed ", "budget", "45,000"));

        assertThat(trip.getPlanningAnswers())
                .containsEntry("pace", "relaxed")
                .containsEntry("interests", "Food")
                .doesNotContainKey("budget");
        assertThat(trip.getBudgetAmount()).isEqualByComparingTo("45000");
    }

    @Test
    void applyAnswers_invalidBudget_isIgnored() {
        Trip trip = new Trip();
        trip.setBudgetAmount(new java.math.BigDecimal("1000"));

        TripGenerationService.applyAnswers(trip, Map.of("budget", "lots"));
        TripGenerationService.applyAnswers(trip, Map.of("budget", "-5"));

        assertThat(trip.getBudgetAmount()).isEqualByComparingTo("1000");
        assertThat(trip.getPlanningAnswers()).isEmpty();
    }

    @Test
    void triggerGeneration_withoutAnswers_reusesStoredAnswers() {
        Trip trip = givenTransitionSucceeds();
        trip.setPlanningAnswers(Map.of("overnightTravel", "no"));

        service.triggerGeneration(TRIP_ID, Map.of());

        org.mockito.ArgumentCaptor<AiGenerateTripRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerateTripRequest.class);
        verify(worker).generate(org.mockito.ArgumentMatchers.eq(TRIP_ID), org.mockito.ArgumentMatchers.eq(USER_ID),
                captor.capture());
        assertThat(captor.getValue().answers()).containsEntry("overnightTravel", "no");
    }

    @Test
    void clarify_ownedTrip_mapsQuestions() {
        Trip trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setDestination("Goa");
        given(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).willReturn(Optional.of(trip));
        given(aiServiceClient.clarify(any())).willReturn(new AiClarifyResponse(List.of(
                new AiClarifyResponse.Question("pace", "Preferred pace?", "single_choice", List.of("relaxed", "packed")),
                new AiClarifyResponse.Question("notes", "Anything else?", "text", null))));

        ClarifyResponse response = service.clarify(TRIP_ID);

        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions().get(0).options()).containsExactly("relaxed", "packed");
        assertThat(response.questions().get(1).options()).isEmpty();
    }

    @Test
    void clarify_notOwned_throwsNotFoundWithoutCallingAi() {
        given(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.clarify(TRIP_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(aiServiceClient, never()).clarify(any());
    }

    private Trip givenTransitionSucceeds() {
        Trip trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setDestination("Goa");
        trip.setPreferences(List.of("beaches"));
        given(tripRepository.updateStatusIfDifferent(TRIP_ID, USER_ID, TripStatus.GENERATING)).willReturn(1);
        given(tripRepository.findByIdAndUserId(TRIP_ID, USER_ID)).willReturn(Optional.of(trip));
        return trip;
    }
}
