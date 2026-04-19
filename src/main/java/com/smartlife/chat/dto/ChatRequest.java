package com.smartlife.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "User message sent to the SmartLife AI assistant")
public record ChatRequest(

        @NotBlank
        @Size(max = 2000)
        @Schema(description = "User's question or message",
                example = "How has my health been this week?")
        String message,

        @Schema(description = "Optional conversation ID for multi-turn chat (UUID string). " +
                "Omit to start a new conversation.")
        String conversationId
) {}
