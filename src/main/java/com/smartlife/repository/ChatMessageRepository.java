package com.smartlife.repository;

import com.smartlife.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdAndUserIdOrderByCreatedAtAsc(String conversationId, Long userId);
}
