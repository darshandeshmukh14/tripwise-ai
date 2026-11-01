package com.tripwise.repository;

import com.tripwise.entity.TripItinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripItineraryRepository extends JpaRepository<TripItinerary, Long> {

    Optional<TripItinerary> findByTripIdAndTripUserId(Long tripId, Long userId);
}
