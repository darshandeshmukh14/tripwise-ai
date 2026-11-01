package com.tripwise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.client.dto.AiItineraryResponse.CostItem;
import com.tripwise.entity.Confidence;
import com.tripwise.entity.CostCategory;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Season;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripCostItem;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.repository.TripCostItemRepository;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.testsupport.AiResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItineraryWriterTest {

    @Mock
    private TripItineraryRepository tripItineraryRepository;
    @Mock
    private TripCostItemRepository tripCostItemRepository;

    @Test
    void saveNewVersion_foreignCurrencyPlan_storesCurrencyFxAndSourceFields() {
        Trip trip = new Trip();
        trip.setId(3L);
        given(tripItineraryRepository.findByTripIdAndTripUserId(3L, 1L)).willReturn(Optional.empty());
        Instant asOf = Instant.parse("2030-01-01T00:00:00Z");
        CostItem stay = new CostItem(CostCategory.STAY, null, "Hotel in Bangkok",
                new AmountRange(new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("120")), Season.PEAK,
                CostSource.WEB_HINT, Confidence.LOW, null, 2, null, "https://example.com/bangkok-hotels");
        AiItineraryResponse base = AiResponses.itinerary("100");
        AiItineraryResponse plan = new AiItineraryResponse(base.tripTitle(), base.destination(), base.origin(),
                base.comfortLevel(), new BigDecimal("8350"), base.totalCost(), base.budgetStatus(), base.season(),
                base.transportOptions(), base.stay(), List.of(stay), base.warnings(), base.days(), "USD",
                new BigDecimal("100"),
                new AiItineraryResponse.Fx("INR", "USD", new BigDecimal("0.0119760479"), asOf, "static"),
                null, null, List.of(), List.of(), false);
        ItineraryWriter writer = new ItineraryWriter(tripItineraryRepository, tripCostItemRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        int version = writer.saveNewVersion(trip, 1L, plan);

        ArgumentCaptor<TripItinerary> itinerary = ArgumentCaptor.forClass(TripItinerary.class);
        verify(tripItineraryRepository).save(itinerary.capture());
        assertThat(version).isEqualTo(1);
        assertThat(itinerary.getValue().getCurrency()).isEqualTo("USD");
        assertThat(itinerary.getValue().getTotalEstimatedCost()).isEqualByComparingTo("100");
        assertThat(itinerary.getValue().getFxRate()).isEqualByComparingTo("0.01197605");
        assertThat(itinerary.getValue().getFxAsOf()).isEqualTo(asOf);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TripCostItem>> items = ArgumentCaptor.forClass(List.class);
        verify(tripCostItemRepository).saveAll(items.capture());
        assertThat(items.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getCurrency()).isEqualTo("USD");
            assertThat(item.getStopSeq()).isEqualTo(2);
            assertThat(item.getSource()).isEqualTo(CostSource.WEB_HINT);
            assertThat(item.getSourceUrl()).isEqualTo("https://example.com/bangkok-hotels");
        });
        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        verify(tripItineraryRepository).save(any(TripItinerary.class));
    }
}
