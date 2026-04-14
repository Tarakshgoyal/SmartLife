package com.smartlife.automation.model;

import com.smartlife.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reminders", indexes = {
        @Index(name = "idx_reminder_user_id", columnList = "user_id"),
        @Index(name = "idx_reminder_scheduled", columnList = "scheduled_at"),
        @Index(name = "idx_reminder_sent", columnList = "sent")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReminderType type;

    // Reference to the entity that triggered this (documentId, expenseId, etc.)
    private UUID relatedEntityId;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    private boolean sent;
    private LocalDateTime sentAt;

    // For recurring reminders
    private boolean recurring;
    @Column(length = 50)
    private String cronExpression;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        sent = false;
    }
}
