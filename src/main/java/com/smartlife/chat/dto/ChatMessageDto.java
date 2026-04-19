package com.smartlife.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A single message in a conversation")
public record ChatMessageDto(
        @Schema(description = "Message sender: 'user' or 'assistant'")
        String role,

        @Schema(description = "Message text content")
        String content,

        @Schema(description = "ISO-8601 timestamp")
        Instant timestamp
) {}
