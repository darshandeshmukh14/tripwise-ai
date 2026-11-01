package com.tripwise;

import com.tripwise.entity.Trip;
import com.tripwise.entity.TripStatus;
import com.tripwise.entity.TransportMode;
import com.tripwise.entity.User;
import com.tripwise.repository.TripRepository;
import com.tripwise.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real PostgreSQL migrations (db/migration) and Hibernate schema validation on PostgreSQL 16,
 * including JSONB columns and the updated_at triggers. Skipped when Docker is not available.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void migrationsApplyAndJsonbRoundTrips() {
        User user = new User();
        user.setEmail("pg-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setFullName("PG User");
        user = userRepository.save(user);

        Trip trip = new Trip();
        trip.setUser(user);
        trip.setDestination("Goa");
        trip.setOrigin("Mumbai");
        trip.setStartDate(LocalDate.of(2030, 1, 1));
        trip.setEndDate(LocalDate.of(2030, 1, 5));
        trip.setTravellersCount(2);
        trip.setStatus(TripStatus.GENERATING);
        trip.setTransportModes(new ArrayList<>(List.of(TransportMode.TRAIN, TransportMode.FLIGHT)));
        trip.setPreferences(new ArrayList<>(List.of("beaches")));
        Long id = tripRepository.save(trip).getId();

        Trip loaded = tripRepository.findByIdAndUserId(id, user.getId()).orElseThrow();
        assertThat(loaded.getTransportModes()).containsExactly(TransportMode.TRAIN, TransportMode.FLIGHT);
        assertThat(loaded.getPreferences()).containsExactly("beaches");
        assertThat(loaded.getCurrency()).isEqualTo("INR");

        int failed = new TransactionTemplate(transactionManager)
                .execute(s -> tripRepository.failStaleGenerations(Instant.now().plus(Duration.ofMinutes(1))));
        assertThat(failed).isEqualTo(1);
    }
}
