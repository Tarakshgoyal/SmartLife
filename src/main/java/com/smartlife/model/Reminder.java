package com.smartlife.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY, ONCE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime dueDate;

    @Enumerated(EnumType.STRING)
    private Frequency frequency;

    private String category;

    @Builder.Default
    @Column(name = "is_completed")
    private boolean completed = false;

    @Column(nullable = false)
    private Long userId;
}
