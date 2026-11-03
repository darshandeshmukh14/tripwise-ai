package com.tripwise.controller;

import com.tripwise.dto.ClarifyResponse;
import com.tripwise.dto.GuestChatReplyResponse;
import com.tripwise.dto.GuestChatRequest;
import com.tripwise.dto.GuestTripRequest;
import com.tripwise.dto.StopSuggestionsResponse;
import com.tripwise.dto.TripDetailResponse;
import com.tripwise.service.GuestPlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public planning endpoints for visitors without an account. Nothing is stored; rate limited per client IP.
 */
@RestController
@RequestMapping("/api/v1/guest")
@RequiredArgsConstructor
public class GuestPlanController {

    private final GuestPlanService guestPlanService;

    @PostMapping("/clarify")
    public ClarifyResponse clarify(@Valid @RequestBody GuestTripRequest request) {
        return guestPlanService.clarify(request);
    }

    @PostMapping("/stops/suggest")
    public StopSuggestionsResponse suggestStops(@Valid @RequestBody GuestTripRequest request) {
        return guestPlanService.suggestStops(request);
    }

    @PostMapping("/generate")
    public TripDetailResponse generate(@Valid @RequestBody GuestTripRequest request, HttpServletRequest http) {
        return guestPlanService.generate(request, http.getRemoteAddr());
    }

    @PostMapping("/messages")
    public GuestChatReplyResponse refine(@Valid @RequestBody GuestChatRequest request, HttpServletRequest http) {
        return guestPlanService.refine(request, http.getRemoteAddr());
    }
}
