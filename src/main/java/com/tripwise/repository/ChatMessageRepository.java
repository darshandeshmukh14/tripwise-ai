package com.tripwise.repository;

import com.tripwise.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findAllByTripIdAndTripUserId(Long tripId, Long userId, Pageable pageable);

    List<ChatMessage> findTop20ByTripIdAndTripUserIdOrderByCreatedAtDescIdDesc(Long tripId, Long userId);
}
