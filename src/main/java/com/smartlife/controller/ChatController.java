package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.ChatMessage;
import com.smartlife.model.User;
import com.smartlife.repository.ChatMessageRepository;
import com.smartlife.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        String conversationId = body.getOrDefault("conversationId", UUID.randomUUID().toString());

        // Save user message
        chatMessageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .role("user")
                .content(message)
                .createdAt(LocalDateTime.now())
                .userId(user.getId())
                .build());

        // Get AI reply
        String reply = ollamaService.generate(message);

        // Save assistant message
        chatMessageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .role("assistant")
                .content(reply)
                .createdAt(LocalDateTime.now())
                .userId(user.getId())
                .build());

        Map<String, Object> data = Map.of(
                "conversationId", conversationId,
                "reply", reply
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHistory(
            @AuthenticationPrincipal User user,
            @PathVariable String conversationId) {
        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, user.getId());
        List<Map<String, Object>> history = messages.stream()
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
