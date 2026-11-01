package com.tripwise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.dto.CreateTripRequest;
import com.tripwise.dto.PageResponse;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.dto.TripStopRequest;
import com.tripwise.dto.TripStopResponse;
import com.tripwise.dto.TripSummaryResponse;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStop;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.TransportMode;
import com.tripwise.entity.TripStatus;
import com.tripwise.entity.User;
import com.tripwise.dto.UpdateTripRequest;
import com.tripwise.exception.GenerationInProgressException;
import com.tripwise.exception.ResourceNotFoundException;
import com.tripwise.exception.StopsTooFarException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.TripCostItemRepository;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.repository.TripRepository;
import com.tripwise.repository.UserRepository;
import com.tripwise.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripItineraryRepository tripItineraryRepository;
    @Mock
    private TripCostItemRepository tripCostItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private TripStopService tripStopService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, tripItineraryRepository, tripCostItemRepository, userRepository,
                currentUserProvider, new TripMapper(), tripStopService, new TransactionTemplate(transactionManager));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void createTrip_withStops_savesCanonicalStopsAndFirstStopAsDestination() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateTripRequest request = new CreateTripRequest("delhi", null, LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(8), 2, null, null, null, null, null, "usd",
                List.of(new TripStopRequest("delhi", null, 2), new TripStopRequest("agra", "in", 1)), null);
        when(tripStopService.resolve(request)).thenReturn(List.of(
                new AiRouteResponse.Stop(1, "Delhi", "IN", 2, true),
                new AiRouteResponse.Stop(2, "Agra", "IN", 1, true)));

        TripDetailResponse response = tripService.createTrip(request);

        assertThat(response.destination()).isEqualTo("Delhi");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.stops()).containsExactly(
                new TripStopResponse(1, "Delhi", "IN", 2), new TripStopResponse(2, "Agra", "IN", 1));
    }

    @Test
    void createTrip_stopsTooFar_savesNothing() {
        CreateTripRequest request = new CreateTripRequest("Delhi", null, LocalDate.now(), LocalDate.now().plusDays(2),
                1, null, null, null, null, null, null,
                List.of(new TripStopRequest("Delhi", null, 1), new TripStopRequest("Chennai", null, 1)), null);
        when(tripStopService.resolve(request)).thenThrow(new StopsTooFarException(List.of("too far")));

        assertThatThrownBy(() -> tripService.createTrip(request)).isInstanceOf(StopsTooFarException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void updateTrip_replacesStopsAfterFlushingOldOnes() {
        Trip trip = trip(5L);
        TripStop old = new TripStop();
        old.setSeq(1);
        old.setCity("Goa");
        old.setNights(3);
        trip.getStops().add(old);
        when(tripRepository.existsByIdAndUserId(5L, USER_ID)).thenReturn(true);
        when(tripRepository.resetToDraftUnlessGenerating(5L, USER_ID)).thenReturn(1);
        when(tripRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(trip));
        when(tripItineraryRepository.findByTripIdAndTripUserId(5L, USER_ID)).thenReturn(Optional.empty());
        UpdateTripRequest request = new UpdateTripRequest("Jaipur", null, LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(6), 2, null, null, null, null, null, null,
                List.of(new TripStopRequest("Jaipur", null, 2), new TripStopRequest("Agra", null, 1)), true);
        when(tripStopService.resolve(request)).thenReturn(List.of(
                new AiRouteResponse.Stop(1, "Jaipur", "IN", 2, true),
                new AiRouteResponse.Stop(2, "Agra", "IN", 1, true)));

        TripDetailResponse response = tripService.updateTrip(5L, request);

        assertThat(response.stops()).extracting(TripStopResponse::city).containsExactly("Jaipur", "Agra");
        assertThat(trip.getStops()).allSatisfy(s -> assertThat(s.getTrip()).isSameAs(trip));
        verify(tripRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void updateTrip_notOwned_throwsNotFoundWithoutCallingAi() {
        when(tripRepository.existsByIdAndUserId(5L, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> tripService.updateTrip(5L, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(tripStopService, never()).resolve(any());
    }

    @Test
    void createTrip_validRequest_savesDraftOwnedByCurrentUser() {
        User userRef = new User();
        userRef.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(userRef);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(100L);
            return t;
        });

        TripDetailResponse response = tripService.createTrip(new CreateTripRequest(
                " Goa ", " Mumbai ", LocalDate.now().plusDays(5), LocalDate.now().plusDays(9), 2,
                new BigDecimal("50000.00"), "RELAXED", null, List.of(TransportMode.TRAIN),
                List.of(" beaches ", "food"), null, null, null));

        ArgumentCaptor<Trip> saved = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(userRef);
        assertThat(saved.getValue().getStatus()).isEqualTo(TripStatus.DRAFT);
        assertThat(saved.getValue().getDestination()).isEqualTo("Goa");
        assertThat(saved.getValue().getOrigin()).isEqualTo("Mumbai");
        assertThat(saved.getValue().getComfortLevel()).isEqualTo(ComfortLevel.STANDARD);
        assertThat(saved.getValue().getTransportModes()).containsExactly(TransportMode.TRAIN);
        assertThat(saved.getValue().getPreferences()).containsExactly("beaches", "food");
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(response.budgetAmount()).isEqualByComparingTo("50000");
        assertThat(response.itinerary()).isNull();
    }

    @Test
    void createTrip_nullPreferences_storesEmptyList() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripDetailResponse response = tripService.createTrip(new CreateTripRequest(
                "Goa", "Pune", LocalDate.now(), LocalDate.now(), 1, null, null, null, null, null, null, null, null));

        assertThat(response.preferences()).isEmpty();
    }

    @Test
    void getTripById_notOwnedOrMissing_throwsResourceNotFound() {
        when(tripRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTripById(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTripById_withItinerary_returnsParsedPlan() throws Exception {
        Trip trip = trip(5L);
        TripItinerary itinerary = new TripItinerary();
        itinerary.setId(9L);
        itinerary.setVersion(2);
        itinerary.setPlanData(new ObjectMapper().readTree("{\"days\":[{\"day\":1}]}"));
        when(tripRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(trip));
        when(tripItineraryRepository.findByTripIdAndTripUserId(5L, USER_ID)).thenReturn(Optional.of(itinerary));

        TripDetailResponse response = tripService.getTripById(5L);

        assertThat(response.itinerary().version()).isEqualTo(2);
        assertThat(response.itinerary().plan().get("days").get(0).get("day").asInt()).isEqualTo(1);
    }

    @Test
    void getUserTrips_queriesOnlyCurrentUser() {
        Pageable pageable = PageRequest.of(0, 10);
        when(tripRepository.findAllByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(trip(1L), trip(2L)), pageable, 2));

        PageResponse<TripSummaryResponse> page = tripService.getUserTrips(pageable);

        assertThat(page.content()).extracting(TripSummaryResponse::id).containsExactly(1L, 2L);
        assertThat(page.totalElements()).isEqualTo(2);
        verify(tripRepository).findAllByUserId(USER_ID, pageable);
    }

    @Test
    void updateTrip_owned_resetsToDraftAndAppliesFields() {
        Trip trip = trip(5L);
        trip.setStatus(TripStatus.DRAFT);
        when(tripRepository.existsByIdAndUserId(5L, USER_ID)).thenReturn(true);
        when(tripRepository.resetToDraftUnlessGenerating(5L, USER_ID)).thenReturn(1);
        when(tripRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(trip));
        when(tripItineraryRepository.findByTripIdAndTripUserId(5L, USER_ID)).thenReturn(Optional.empty());

        TripDetailResponse response = tripService.updateTrip(5L, new UpdateTripRequest(
                "Manali", "Delhi", LocalDate.now().plusDays(3), LocalDate.now().plusDays(6), 3,
                new BigDecimal("40000"), null, ComfortLevel.PREMIUM, List.of(TransportMode.BUS), List.of(),
                null, null, null));

        assertThat(response.destination()).isEqualTo("Manali");
        assertThat(response.origin()).isEqualTo("Delhi");
        assertThat(response.comfortLevel()).isEqualTo(ComfortLevel.PREMIUM);
        assertThat(trip.getTransportModes()).containsExactly(TransportMode.BUS);
    }

    @Test
    void updateTrip_generating_throwsConflict() {
        when(tripRepository.resetToDraftUnlessGenerating(5L, USER_ID)).thenReturn(0);
        when(tripRepository.existsByIdAndUserId(5L, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> tripService.updateTrip(5L, null))
                .isInstanceOf(GenerationInProgressException.class);
    }

    @Test
    void deleteTrip_notOwned_throwsNotFound() {
        when(tripRepository.deleteUnlessGenerating(5L, USER_ID)).thenReturn(0);
        when(tripRepository.existsByIdAndUserId(5L, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> tripService.deleteTrip(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Trip trip(Long id) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setDestination("Goa");
        trip.setStartDate(LocalDate.of(2027, 1, 1));
        trip.setEndDate(LocalDate.of(2027, 1, 5));
        trip.setTravellersCount(2);
        return trip;
    }
}
