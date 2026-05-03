package com.smartlife.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "health_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    private Integer steps;
    private Double sleepHours;
    private Integer heartRate;
    private Integer calories;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Long userId;
}
