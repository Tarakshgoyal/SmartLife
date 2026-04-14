package com.smartlife.health.dto;

import com.smartlife.health.model.HealthLog;
import com.smartlife.health.service.HealthPatternAnalyzer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HealthLogDto(
        UUID id,
        LocalDate logDate,
        Integer systolicBp,
        Integer diastolicBp,
        Double bloodGlucose,
        Double weight,
        Double temperature,
        Integer heartRate,
        Double sleepHours,
        Integer sleepQuality,
        Integer moodScore,
        Integer stressLevel,
        Integer exerciseMinutes,
        String symptoms,
        Integer waterIntake,
        String notes,
        List<HealthPatternAnalyzer.HealthWarning> warnings
) {
    public static HealthLogDto from(HealthLog h, List<HealthPatternAnalyzer.HealthWarning> warnings) {
        return new HealthLogDto(h.getId(), h.getLogDate(), h.getSystolicBp(), h.getDiastolicBp(),
                h.getBloodGlucose(), h.getWeight(), h.getTemperature(), h.getHeartRate(),
                h.getSleepHours(), h.getSleepQuality(), h.getMoodScore(), h.getStressLevel(),
                h.getExerciseMinutes(), h.getSymptoms(), h.getWaterIntake(), h.getNotes(), warnings);
    }
}
