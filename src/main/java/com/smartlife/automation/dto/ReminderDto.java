package com.smartlife.automation.dto;

import com.smartlife.automation.model.Reminder;
import com.smartlife.automation.model.ReminderType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReminderDto(
        UUID id,
        String title,
        String message,
        ReminderType type,
        UUID relatedEntityId,
        LocalDateTime scheduledAt,
        boolean sent,
        LocalDateTime sentAt,
        boolean recurring
) {
    public static ReminderDto from(Reminder r) {
        return new ReminderDto(r.getId(), r.getTitle(), r.getMessage(), r.getType(),
                r.getRelatedEntityId(), r.getScheduledAt(), r.isSent(), r.getSentAt(), r.isRecurring());
    }
}
