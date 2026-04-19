package com.smartlife.chat.controller;

import com.smartlife.auth.model.User;
import com.smartlife.chat.dto.ChatRequest;
import com.smartlife.chat.dto.ChatResponse;
import com.smartlife.chat.service.ChatService;
import com.smartlife.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "AI Chatbot", description = "RAG-powered personal AI assistant with full life context")
public class ChatController {

    private final ChatService chatService;

    @Operation(
        summary = "Send a message to the SmartLife AI assistant",
        description = "The assistant has real-time access to your expenses, health logs, documents, and reminders. "
            + "Include conversationId from a previous response to continue a conversation."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal User user) {
        ChatResponse response = chatService.chat(request, user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Clear/reset a conversation by ID")
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> clearConversation(
            @Parameter(description = "Conversation ID to clear")
            @PathVariable String conversationId,
            @AuthenticationPrincipal User user) {
        chatService.clearConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation cleared"));
    }
}
