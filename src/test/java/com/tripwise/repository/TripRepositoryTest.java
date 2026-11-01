package com.tripwise.repository;

import com.tripwise.entity.Trip;
import com.tripwise.entity.TripStatus;
import com.tripwise.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conditional JPQL updates/deletes against the H2 Flyway schema (each test runs in a rolled-back transaction).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("h2")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:trip-repo-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
class TripRepositoryTest {

    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(user());
        bob = userRepository.save(user());
    }

    @Test
    void failStaleGenerations_onlyFailsGeneratingTripsOlderThanCutoff() {
        Trip generating = tripRepository.save(trip(alice, TripStatus.GENERATING));
        Trip draft = tripRepository.save(trip(alice, TripStatus.DRAFT));
        tripRepository.flush();

        assertThat(tripRepository.failStaleGenerations(Instant.now().minus(Duration.ofHours(1)))).isZero();
        assertThat(tripRepository.failStaleGenerations(Instant.now().plus(Duration.ofHours(1)))).isEqualTo(1);

        assertThat(tripRepository.findById(generating.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.FAILED);
        assertThat(tripRepository.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.DRAFT);
    }

    @Test
    void resetToDraftUnlessGenerating_respectsOwnershipAndGeneratingState() {
        Trip failed = tripRepository.save(trip(alice, TripStatus.FAILED));
        Trip generating = tripRepository.save(trip(alice, TripStatus.GENERATING));
        tripRepository.flush();

        assertThat(tripRepository.resetToDraftUnlessGenerating(failed.getId(), bob.getId())).isZero();
        assertThat(tripRepository.resetToDraftUnlessGenerating(generating.getId(), alice.getId())).isZero();
        assertThat(tripRepository.resetToDraftUnlessGenerating(failed.getId(), alice.getId())).isEqualTo(1);
        assertThat(tripRepository.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.DRAFT);
    }

    @Test
    void deleteUnlessGenerating_respectsOwnershipAndGeneratingState() {
        Trip completed = tripRepository.save(trip(alice, TripStatus.COMPLETED));
        Trip generating = tripRepository.save(trip(alice, TripStatus.GENERATING));
        tripRepository.flush();

        assertThat(tripRepository.deleteUnlessGenerating(completed.getId(), bob.getId())).isZero();
        assertThat(tripRepository.deleteUnlessGenerating(generating.getId(), alice.getId())).isZero();
        assertThat(tripRepository.deleteUnlessGenerating(completed.getId(), alice.getId())).isEqualTo(1);
        entityManager.clear();
        assertThat(tripRepository.findById(completed.getId())).isEmpty();
        assertThat(tripRepository.findById(generating.getId())).isPresent();
    }

    private static User user() {
        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setFullName("Test");
        return user;
    }

    private static Trip trip(User owner, TripStatus status) {
        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setDestination("Goa");
        trip.setOrigin("Mumbai");
        trip.setStartDate(LocalDate.of(2030, 1, 1));
        trip.setEndDate(LocalDate.of(2030, 1, 5));
        trip.setTravellersCount(2);
        trip.setStatus(status);
        return trip;
    }
}
