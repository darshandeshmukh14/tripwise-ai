package com.tripwise.service;

import com.tripwise.config.TripGenerationProperties;
import com.tripwise.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Recovers trips stuck in GENERATING (e.g. the app restarted mid-call), so frontend polling always terminates.
 */
@Slf4j
@Component
public class TripGenerationSweeper {

    private final TripRepository tripRepository;
    private final TripGenerationProperties properties;
    private final Clock clock;
    // Generations started before this instance existed can never complete: the in-memory queue was lost
    private final Instant startedAt;

    public TripGenerationSweeper(TripRepository tripRepository, TripGenerationProperties properties, Clock clock) {
        this.tripRepository = tripRepository;
        this.properties = properties;
        this.clock = clock;
        this.startedAt = Instant.now(clock);
    }

    /**
     * Single-instance assumption: with several instances this would fail generations owned by the others.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public int failGenerationsInterruptedByRestart() {
        int failed = tripRepository.failStaleGenerations(startedAt);
        if (failed > 0) {
            log.warn("Marked {} trip(s) left GENERATING by a previous run as FAILED", failed);
        }
        return failed;
    }

    @Scheduled(fixedDelayString = "${trip-generation.sweep-interval:PT1M}",
            initialDelayString = "${trip-generation.sweep-interval:PT1M}")
    @Transactional
    public int failStaleGenerations() {
        Instant cutoff = Instant.now(clock).minus(properties.staleAfter());
        int failed = tripRepository.failStaleGenerations(cutoff);
        if (failed > 0) {
            log.warn("Marked {} trip(s) stuck in GENERATING since before {} as FAILED", failed, cutoff);
        }
        return failed;
    }
}
