package com.smartlife.automation.dto;

import com.smartlife.automation.model.ReminderType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReminderCreateRequest(
        @NotBlank String title,
        String message,
        @NotNull ReminderType type,
        UUID relatedEntityId,
        @NotNull @Future LocalDateTime scheduledAt,
        Boolean recurring,
        String cronExpression
) {}
