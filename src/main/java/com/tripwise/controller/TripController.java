package com.tripwise.controller;

import com.tripwise.dto.ClarifyResponse;
import com.tripwise.dto.CreateTripRequest;
import com.tripwise.dto.GenerateTripRequest;
import com.tripwise.dto.PageResponse;
import com.tripwise.dto.StopSuggestionsResponse;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.dto.TripStatusResponse;
import com.tripwise.dto.TripSummaryResponse;
import com.tripwise.dto.UpdateTripRequest;
import com.tripwise.service.TripGenerationService;
import com.tripwise.service.TripService;
import com.tripwise.service.TripStopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TripGenerationService tripGenerationService;
    private final TripStopService tripStopService;

    @PostMapping
    public ResponseEntity<TripDetailResponse> createTrip(@Valid @RequestBody CreateTripRequest request) {
        TripDetailResponse created = tripService.createTrip(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<TripSummaryResponse> getUserTrips(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tripService.getUserTrips(pageable);
    }

    @GetMapping("/{id}")
    public TripDetailResponse getTripById(@PathVariable Long id) {
        return tripService.getTripById(id);
    }

    @PutMapping("/{id}")
    public TripDetailResponse updateTrip(@PathVariable Long id, @Valid @RequestBody UpdateTripRequest request) {
        return tripService.updateTrip(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id) {
        tripService.deleteTrip(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clarify")
    public ClarifyResponse clarify(@PathVariable Long id) {
        return tripGenerationService.clarify(id);
    }

    @PostMapping("/{id}/stops/suggest")
    public StopSuggestionsResponse suggestStops(@PathVariable Long id) {
        return tripStopService.suggest(id);
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<TripStatusResponse> generateItinerary(
            @PathVariable Long id, @Valid @RequestBody(required = false) GenerateTripRequest request) {
        Map<String, String> answers = request == null || request.answers() == null ? Map.of() : request.answers();
        TripStatusResponse accepted = tripGenerationService.triggerGeneration(id, answers);
        URI statusUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/trips/{id}/status").buildAndExpand(id).toUri();
        return ResponseEntity.accepted().location(statusUri).body(accepted);
    }

    @GetMapping("/{id}/status")
    public TripStatusResponse getTripStatus(@PathVariable Long id) {
        return tripService.getTripStatus(id);
    }
}
