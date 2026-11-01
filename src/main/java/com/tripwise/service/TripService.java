package com.tripwise.service;

import com.tripwise.client.dto.AiRouteResponse;
import com.tripwise.dto.CreateTripRequest;
import com.tripwise.dto.PageResponse;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.dto.TripStatusResponse;
import com.tripwise.dto.TripSummaryResponse;
import com.tripwise.dto.UpdateTripRequest;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripCostItem;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.GenerationInProgressException;
import com.tripwise.exception.ResourceNotFoundException;
import com.tripwise.exception.StopsTooFarException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.TripCostItemRepository;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.repository.TripRepository;
import com.tripwise.repository.UserRepository;
import com.tripwise.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripItineraryRepository tripItineraryRepository;
    private final TripCostItemRepository tripCostItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TripMapper tripMapper;
    private final TripStopService tripStopService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Multi-city stops are validated by the AI service first (outside the transaction), then saved with the trip.
     */
    public TripDetailResponse createTrip(CreateTripRequest request) {
        Long userId = currentUserId();
        List<AiRouteResponse.Stop> stops = tripStopService.resolve(request);
        return transactionTemplate.execute(status -> {
            Trip trip = tripMapper.toEntity(request);
            trip.setUser(userRepository.getReferenceById(userId));
            trip.setStatus(TripStatus.DRAFT);
            setStops(trip, stops);

            Trip saved = tripRepository.save(trip);
            log.info("Created trip id={} for user id={}", saved.getId(), userId);
            return tripMapper.toDetail(saved, null, List.of());
        });
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getTripById(Long id) {
        Long userId = currentUserId();
        Trip trip = findOwned(id, userId);
        return toDetail(trip, userId);
    }

    @Transactional(readOnly = true)
    public TripStatusResponse getTripStatus(Long id) {
        Trip trip = findOwned(id, currentUserId());
        return new TripStatusResponse(trip.getId(), trip.getStatus());
    }

    @Transactional(readOnly = true)
    public PageResponse<TripSummaryResponse> getUserTrips(Pageable pageable) {
        return PageResponse.from(tripRepository.findAllByUserId(currentUserId(), pageable), tripMapper::toSummary);
    }

    /**
     * Replaces the editable fields and resets the trip to DRAFT (the existing itinerary is kept but is now stale).
     *
     * @throws GenerationInProgressException while generation is running
     * @throws StopsTooFarException          if the new stops break the route rules (nothing is changed)
     */
    public TripDetailResponse updateTrip(Long id, UpdateTripRequest request) {
        Long userId = currentUserId();
        if (!tripRepository.existsByIdAndUserId(id, userId)) {
            throw new ResourceNotFoundException("Trip not found.");
        }
        List<AiRouteResponse.Stop> stops = tripStopService.resolve(request);
        return transactionTemplate.execute(status -> {
            if (tripRepository.resetToDraftUnlessGenerating(id, userId) == 0) {
                throw notFoundOrBusy(id, userId);
            }
            Trip trip = findOwned(id, userId);
            tripMapper.apply(request, trip);
            if (!trip.getStops().isEmpty()) {
                trip.getStops().clear();
                // Hibernate inserts before it deletes; flush the orphan removals so (trip_id, seq) stays unique
                tripRepository.flush();
            }
            setStops(trip, stops);
            tripRepository.flush();
            log.info("Updated trip id={}", id);
            return toDetail(trip, userId);
        });
    }

    private void setStops(Trip trip, List<AiRouteResponse.Stop> stops) {
        if (stops.isEmpty()) {
            return;
        }
        trip.getStops().addAll(tripMapper.toStops(trip, stops));
        trip.setDestination(stops.get(0).city().trim());
    }

    /**
     * @throws GenerationInProgressException while generation is running
     */
    @Transactional
    public void deleteTrip(Long id) {
        Long userId = currentUserId();
        if (tripRepository.deleteUnlessGenerating(id, userId) == 0) {
            throw notFoundOrBusy(id, userId);
        }
        log.info("Deleted trip id={}", id);
    }

    private TripDetailResponse toDetail(Trip trip, Long userId) {
        TripItinerary itinerary = tripItineraryRepository.findByTripIdAndTripUserId(trip.getId(), userId)
                .orElse(null);
        List<TripCostItem> costItems = itinerary == null
                ? List.of()
                : tripCostItemRepository.findAllByTripIdAndTripUserIdAndItineraryVersionOrderByIdAsc(
                        trip.getId(), userId, itinerary.getVersion());
        return tripMapper.toDetail(trip, itinerary, costItems);
    }

    private Trip findOwned(Long id, Long userId) {
        return tripRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found."));
    }

    private RuntimeException notFoundOrBusy(Long id, Long userId) {
        return tripRepository.existsByIdAndUserId(id, userId)
                ? new GenerationInProgressException()
                : new ResourceNotFoundException("Trip not found.");
    }

    private Long currentUserId() {
        return currentUserProvider.getCurrentUserId();
    }
}
