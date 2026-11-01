package com.tripwise.repository;

import com.tripwise.entity.Trip;
import com.tripwise.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * All trip reads must go through the user-scoped methods below; never expose a trip found by id alone.
 */
public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    Page<Trip> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Loads an owned trip with a row lock, serialising concurrent itinerary writes for the same trip.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id and t.user.id = :userId")
    Optional<Trip> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Atomically sets the status of an owned trip unless it already has that status.
     * Returns 0 when the trip is missing, not owned, or already in {@code status}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Trip t set t.status = :status "
            + "where t.id = :id and t.user.id = :userId and t.status <> :status")
    int updateStatusIfDifferent(@Param("id") Long id, @Param("userId") Long userId,
                                @Param("status") TripStatus status);

    /**
     * Resets an owned trip to DRAFT before an edit, unless generation is running.
     * The row lock taken here serialises the edit with a concurrent generate request.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Trip t set t.status = com.tripwise.entity.TripStatus.DRAFT "
            + "where t.id = :id and t.user.id = :userId and t.status <> com.tripwise.entity.TripStatus.GENERATING")
    int resetToDraftUnlessGenerating(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Child rows (itinerary, cost items, chat) are removed by ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Trip t "
            + "where t.id = :id and t.user.id = :userId and t.status <> com.tripwise.entity.TripStatus.GENERATING")
    int deleteUnlessGenerating(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * System-wide recovery of trips left in GENERATING (e.g. after a crash). Not user-scoped by design.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Trip t set t.status = com.tripwise.entity.TripStatus.FAILED "
            + "where t.status = com.tripwise.entity.TripStatus.GENERATING and t.updatedAt < :cutoff")
    int failStaleGenerations(@Param("cutoff") Instant cutoff);
}
