package com.smartlife.health.model;

import com.smartlife.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "health_logs", indexes = {
        @Index(name = "idx_health_user_id", columnList = "user_id"),
        @Index(name = "idx_health_date", columnList = "log_date")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate logDate;

    // Vitals
    private Integer systolicBp;     // mmHg
    private Integer diastolicBp;    // mmHg
    private Double bloodGlucose;    // mg/dL
    private Double weight;          // kg
    private Double temperature;     // °C

    // Heart rate
    private Integer heartRate;      // bpm

    // Sleep
    private Double sleepHours;
    private Integer sleepQuality;   // 1-10 scale

    // Energy & mood
    private Integer energyLevel;    // 1-10 scale
    private Integer moodScore;      // 1-10 scale
    private Integer stressLevel;    // 1-10 scale

    // Exercise
    private Integer exerciseMinutes;
    private String exerciseType;

    // Symptoms (comma-separated)
    @Column(columnDefinition = "TEXT")
    private String symptoms;

    // Medications taken (comma-separated)
    @Column(columnDefinition = "TEXT")
    private String medications;

    // Water intake (glasses)
    private Integer waterIntake;

    @Column(length = 1000)
    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (logDate == null) logDate = LocalDate.now();
    }
}
