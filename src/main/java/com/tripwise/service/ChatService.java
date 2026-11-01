package com.tripwise.service;

import com.tripwise.client.AiServiceClient;
import com.tripwise.client.dto.AiRefineRequest;
import com.tripwise.client.dto.AiRefineResponse;
import com.tripwise.dto.ChatMessageResponse;
import com.tripwise.dto.ChatReplyResponse;
import com.tripwise.dto.PageResponse;
import com.tripwise.entity.ChatMessage;
import com.tripwise.entity.SenderRole;
import com.tripwise.entity.Trip;
import com.tripwise.entity.TripItinerary;
import com.tripwise.entity.TripStatus;
import com.tripwise.exception.GenerationInProgressException;
import com.tripwise.exception.ItineraryConflictException;
import com.tripwise.exception.ItineraryNotReadyException;
import com.tripwise.exception.ResourceNotFoundException;
import com.tripwise.mapper.TripMapper;
import com.tripwise.repository.ChatMessageRepository;
import com.tripwise.repository.TripItineraryRepository;
import com.tripwise.repository.TripRepository;
import com.tripwise.security.CurrentUserProvider;
import com.tripwise.security.WebHintLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat-based refinement of a generated itinerary. The AI call runs outside any transaction; the user message,
 * assistant reply and (optional) new itinerary version are persisted together only after the AI call succeeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final TripRepository tripRepository;
    private final TripItineraryRepository tripItineraryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ItineraryWriter itineraryWriter;
    private final AiServiceClient aiServiceClient;
    private final TripMapper tripMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;
    private final WebHintLimiter webHintLimiter;

    public PageResponse<ChatMessageResponse> getMessages(Long tripId, Pageable pageable) {
        Long userId = currentUserProvider.getCurrentUserId();
        return transactionTemplate.execute(status -> {
            if (!tripRepository.existsByIdAndUserId(tripId, userId)) {
                throw new ResourceNotFoundException("Trip not found.");
            }
            return PageResponse.from(chatMessageRepository.findAllByTripIdAndTripUserId(tripId, userId, pageable),
                    ChatService::toResponse);
        });
    }

    /**
     * @param expectedVersion itinerary version the client is looking at (optional)
     * @throws ItineraryNotReadyException    if no itinerary has been generated yet
     * @throws GenerationInProgressException if generation is running
     * @throws ItineraryConflictException    if the itinerary changed since {@code expectedVersion} or while the AI
     *                                       was answering
     */
    public ChatReplyResponse sendMessage(Long tripId, String content, Integer expectedVersion) {
        Long userId = currentUserProvider.getCurrentUserId();
        String message = content.trim();

        PreparedRefine prepared = transactionTemplate.execute(
                status -> buildRequest(tripId, userId, message, expectedVersion));
        AiRefineResponse reply = aiServiceClient.refine(prepared.request());

        return transactionTemplate.execute(status -> persistReply(tripId, userId, message, reply, prepared.version()));
    }

    private PreparedRefine buildRequest(Long tripId, Long userId, String message, Integer expectedVersion) {
        Trip trip = loadChattableTrip(tripId, userId);
        TripItinerary itinerary = tripItineraryRepository.findByTripIdAndTripUserId(tripId, userId)
                .orElseThrow(ItineraryNotReadyException::new);
        if (expectedVersion != null && !expectedVersion.equals(itinerary.getVersion())) {
            throw new ItineraryConflictException();
        }

        List<ChatMessage> recent = new ArrayList<>(
                chatMessageRepository.findTop20ByTripIdAndTripUserIdOrderByCreatedAtDescIdDesc(tripId, userId));
        Collections.reverse(recent);
        List<AiRefineRequest.Turn> history = recent.stream()
                .filter(m -> m.getSenderRole() != SenderRole.SYSTEM)
                .map(m -> new AiRefineRequest.Turn(m.getSenderRole().name(), m.getContent()))
                .toList();

        AiRefineRequest request = new AiRefineRequest(tripId,
                tripMapper.toAiRequest(trip).withAllowWebHints(webHintLimiter.tryConsume(userId)),
                itinerary.getPlanData(), history, message);
        return new PreparedRefine(request, itinerary.getVersion());
    }

    private ChatReplyResponse persistReply(Long tripId, Long userId, String message, AiRefineResponse reply,
                                           int baseVersion) {
        // Re-check under a row lock: the trip may have been deleted, regenerated or refined by another request
        // while the AI was answering
        Trip trip = tripRepository.findByIdAndUserIdForUpdate(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found."));
        if (trip.getStatus() == TripStatus.GENERATING) {
            throw new GenerationInProgressException();
        }
        Integer currentVersion = tripItineraryRepository.findByTripIdAndTripUserId(tripId, userId)
                .map(TripItinerary::getVersion).orElse(null);
        boolean updated = reply.updatedPlan() != null;
        if (updated && !Integer.valueOf(baseVersion).equals(currentVersion)) {
            log.info("Discarded chat refinement of trip id={}: itinerary changed from version {} to {}",
                    tripId, baseVersion, currentVersion);
            throw new ItineraryConflictException();
        }

        ChatMessage userMessage = chatMessageRepository.save(newMessage(trip, SenderRole.USER, message));
        ChatMessage assistantMessage = chatMessageRepository.save(
                newMessage(trip, SenderRole.ASSISTANT, reply.assistantMessage()));

        Integer version = currentVersion;
        if (updated) {
            version = itineraryWriter.saveNewVersion(trip, userId, reply.updatedPlan());
            log.info("Chat refined itinerary of trip id={} to version {}", tripId, version);
        }
        chatMessageRepository.flush();
        return new ChatReplyResponse(toResponse(userMessage), toResponse(assistantMessage), updated, version);
    }

    private record PreparedRefine(AiRefineRequest request, int version) {
    }

    private Trip loadChattableTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found."));
        if (trip.getStatus() == TripStatus.GENERATING) {
            throw new GenerationInProgressException();
        }
        return trip;
    }

    private static ChatMessage newMessage(Trip trip, SenderRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setTrip(trip);
        message.setSenderRole(role);
        message.setContent(content);
        return message;
    }

    private static ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getSenderRole(), message.getContent(),
                message.getCreatedAt());
    }
}
