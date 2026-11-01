package com.tripwise.dto;

import com.tripwise.entity.BudgetStatus;
import com.tripwise.entity.CostCategory;
import com.tripwise.entity.Confidence;
import com.tripwise.entity.CostSource;
import com.tripwise.entity.Season;
import com.tripwise.entity.TransportMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param budgetStatus UNKNOWN when the itinerary was priced in a different currency than the trip's budget
 */
public record CostBreakdownResponse(
        BigDecimal totalMin,
        BigDecimal totalExpected,
        BigDecimal totalMax,
        String currency,
        BudgetStatus budgetStatus,
        List<Item> items
) {

    /**
     * @param stopSeq   multi-city stop (1..6) of a stay item
     * @param legIndex  multi-city transport leg (0 = from the origin)
     * @param sourceUrl web page backing a WEB_HINT price
     */
    public record Item(
            CostCategory category,
            TransportMode mode,
            String label,
            BigDecimal amountMin,
            BigDecimal amountExpected,
            BigDecimal amountMax,
            Season season,
            CostSource source,
            Confidence confidence,
            String assumptions,
            Integer stopSeq,
            Integer legIndex,
            String sourceUrl
    ) {
    }
}
