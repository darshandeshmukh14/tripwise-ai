package com.tripwise.service;

import com.tripwise.config.TripGenerationProperties;
import com.tripwise.repository.TripRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripGenerationSweeperTest {

    @Test
    void failStaleGenerations_usesClockMinusStaleAfterAsCutoff() {
        TripRepository repository = mock(TripRepository.class);
        Instant now = Instant.parse("2030-01-01T12:00:00Z");
        TripGenerationSweeper sweeper = new TripGenerationSweeper(repository,
                new TripGenerationProperties(Duration.ofMinutes(10)), Clock.fixed(now, ZoneOffset.UTC));
        when(repository.failStaleGenerations(Instant.parse("2030-01-01T11:50:00Z"))).thenReturn(3);

        assertThat(sweeper.failStaleGenerations()).isEqualTo(3);
        verify(repository).failStaleGenerations(Instant.parse("2030-01-01T11:50:00Z"));
    }

    @Test
    void failGenerationsInterruptedByRestart_failsEverythingStartedBeforeThisInstance() {
        TripRepository repository = mock(TripRepository.class);
        Instant startedAt = Instant.parse("2030-01-01T12:00:00Z");
        TripGenerationSweeper sweeper = new TripGenerationSweeper(repository,
                new TripGenerationProperties(Duration.ofMinutes(10)), Clock.fixed(startedAt, ZoneOffset.UTC));
        when(repository.failStaleGenerations(startedAt)).thenReturn(2);

        assertThat(sweeper.failGenerationsInterruptedByRestart()).isEqualTo(2);
    }
}
