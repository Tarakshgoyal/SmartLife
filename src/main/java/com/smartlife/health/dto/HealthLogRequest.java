package com.smartlife.health.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public record HealthLogRequest(
        LocalDate logDate,
        Integer systolicBp,
        Integer diastolicBp,
        Double bloodGlucose,
        Double weight,
        Double temperature,
        Integer heartRate,
        Double sleepHours,
        @Min(1) @Max(10) Integer sleepQuality,
        @Min(1) @Max(10) Integer energyLevel,
        @Min(1) @Max(10) Integer moodScore,
        @Min(1) @Max(10) Integer stressLevel,
        Integer exerciseMinutes,
        String exerciseType,
        String symptoms,
        String medications,
        Integer waterIntake,
        String notes
) {}
