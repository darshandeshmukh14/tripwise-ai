package com.tripwise.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripwise.client.dto.AiItineraryResponse;
import com.tripwise.client.dto.AiItineraryResponse.AmountRange;
import com.tripwise.client.dto.AiItineraryResponse.CostItem;
import com.tripwise.entity.BudgetStatus;
import com.tripwise.entity.ComfortLevel;
import com.tripwise.entity.Confidence;
import com.tripwise.entity.CostCategory;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Season;
import com.tripwise.entity.TransportMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Valid AI itinerary responses for tests: two cost items (transport 20%, stay 80%) that add up to the total.
 */
public final class AiResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiResponses() {
    }

    public static AiItineraryResponse itinerary(String total) {
        BigDecimal t = new BigDecimal(total);
        BigDecimal transport = t.multiply(new BigDecimal("0.2")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stay = t.subtract(transport);
        try {
            return new AiItineraryResponse("Goa Escape", "Goa", "Mumbai", ComfortLevel.STANDARD, t,
                    new AmountRange(t, t, t), BudgetStatus.WITHIN,
                    new AiItineraryResponse.SeasonInfo(Season.PEAK, new BigDecimal("1.35"), "Winter peak"),
                    List.of(), null,
                    List.of(item(CostCategory.TRANSPORT, TransportMode.TRAIN, "Train", transport),
                            item(CostCategory.STAY, null, "Hotel", stay)),
                    List.of(),
                    MAPPER.readTree("[{\"day\":1,\"activities\":[\"Beach\"]}]"),
                    null, null, null, null, null, null, null, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static CostItem item(CostCategory category, TransportMode mode, String label, BigDecimal expected) {
        return new CostItem(category, mode, label, new AmountRange(expected, expected, expected), Season.PEAK,
                CostSource.ESTIMATE, Confidence.MEDIUM, null, null, null, null);
    }
}
