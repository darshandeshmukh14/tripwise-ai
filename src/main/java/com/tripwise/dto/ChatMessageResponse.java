package com.tripwise.dto;

import com.tripwise.entity.SenderRole;

import java.time.Instant;

public record ChatMessageResponse(Long id, SenderRole senderRole, String content, Instant createdAt) {
}
