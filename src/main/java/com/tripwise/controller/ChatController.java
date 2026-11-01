package com.tripwise.controller;

import com.tripwise.dto.ChatMessageResponse;
import com.tripwise.dto.ChatReplyResponse;
import com.tripwise.dto.PageResponse;
import com.tripwise.dto.SendMessageRequest;
import com.tripwise.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public PageResponse<ChatMessageResponse> getMessages(
            @PathVariable Long tripId,
            @PageableDefault(size = 50, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return chatService.getMessages(tripId, pageable);
    }

    @PostMapping
    public ChatReplyResponse sendMessage(@PathVariable Long tripId, @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(tripId, request.content(), request.expectedVersion());
    }
}
