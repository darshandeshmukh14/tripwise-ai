package com.tripwise.client.dto;

import java.util.List;

/**
 * Body of {@code POST /internal/v1/stops/route}: validates (and optionally reorders) multi-city stops.
 */
public record AiRouteRequest(String origin, List<AiStop> stops, boolean optimizeOrder) {
}
