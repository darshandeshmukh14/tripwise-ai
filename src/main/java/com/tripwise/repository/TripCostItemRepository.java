package com.tripwise.repository;

import com.tripwise.entity.TripCostItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripCostItemRepository extends JpaRepository<TripCostItem, Long> {

    List<TripCostItem> findAllByTripIdAndTripUserIdAndItineraryVersionOrderByIdAsc(Long tripId, Long userId,
                                                                                    Integer itineraryVersion);
}
