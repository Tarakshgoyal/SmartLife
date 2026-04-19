package com.smartlife.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI assistant response")
public record ChatResponse(

        @Schema(description = "Conversation ID — save this for follow-up messages")
        String conversationId,

        @Schema(description = "The AI assistant's reply")
        String reply,

        @Schema(description = "Full conversation history including this exchange")
        List<ChatMessageDto> history,

        @Schema(description = "Whether Ollama/Llama was available; false = fallback response")
        boolean aiPowered
) {}
