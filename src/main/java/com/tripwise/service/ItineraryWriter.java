package com.tripwise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripCostItem;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.repository.TripCostItemRepository;
import com.tripwise.repository.TripItineraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Persists a validated AI plan as the trip's next itinerary version, with its cost items, and completes the trip.
 * Must run inside the caller's transaction, with {@code trip} already loaded through an ownership-scoped query.
 */
@Component
@RequiredArgsConstructor
public class ItineraryWriter {

    private final TripItineraryRepository tripItineraryRepository;
    private final TripCostItemRepository tripCostItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public int saveNewVersion(Trip trip, Long userId, AiItineraryResponse plan) {
        TripItinerary itinerary = tripItineraryRepository.findByTripIdAndTripUserId(trip.getId(), userId)
                .map(existing -> {
                    existing.setVersion(existing.getVersion() + 1);
                    return existing;
                })
                .orElseGet(() -> {
                    TripItinerary created = new TripItinerary();
                    created.setTrip(trip);
                    created.setVersion(1);
                    return created;
                });

        String currency = plan.currencyOrDefault();
        itinerary.setTotalEstimatedCost(money(plan.total()));
        itinerary.setCurrency(currency);
        itinerary.setFxRate(plan.fx() == null ? null : plan.fx().rate().setScale(8, RoundingMode.HALF_UP));
        itinerary.setFxAsOf(plan.fx() == null ? null : plan.fx().asOf());
        itinerary.setPlanData(objectMapper.valueToTree(plan));
        tripItineraryRepository.save(itinerary);

        int version = itinerary.getVersion();
        List<TripCostItem> items = plan.costBreakdown().stream()
                .map(line -> toEntity(trip, version, currency, line))
                .toList();
        tripCostItemRepository.saveAll(items);

        trip.setStatus(TripStatus.COMPLETED);
        return version;
    }

    private static TripCostItem toEntity(Trip trip, int version, String currency, AiItineraryResponse.CostItem line) {
        TripCostItem item = new TripCostItem();
        item.setTrip(trip);
        item.setItineraryVersion(version);
        item.setCurrency(currency);
        item.setCategory(line.category());
        item.setMode(line.mode());
        item.setLabel(truncate(line.label(), 255));
        item.setAmountMin(money(line.amount().min()));
        item.setAmountExpected(money(line.amount().expected()));
        item.setAmountMax(money(line.amount().max()));
        item.setSeason(line.season());
        item.setSource(line.source() == null ? CostSource.ESTIMATE : line.source());
        item.setConfidence(line.confidence());
        item.setAssumptions(truncate(line.assumptions(), 2000));
        item.setStopSeq(line.stopSeq());
        item.setLegIndex(line.legIndex());
        // Validated as http(s) and at most 2000 chars by AiResponseValidator
        item.setSourceUrl(line.sourceUrl());
        return item;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
