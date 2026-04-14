package com.smartlife.health.service;

import com.smartlife.auth.model.User;
import com.smartlife.common.exception.SmartLifeException;
import com.smartlife.health.dto.*;
import com.smartlife.health.model.HealthLog;
import com.smartlife.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {

    private final HealthLogRepository healthLogRepository;
    private final HealthPatternAnalyzer patternAnalyzer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public HealthLogDto logHealth(HealthLogRequest request, User user) {
        // Check if already logged today
        healthLogRepository.findByUserIdAndLogDate(user.getId(),
                request.logDate() != null ? request.logDate() : LocalDate.now())
                .ifPresent(existing -> {
                    throw new SmartLifeException(
                            "Health log already exists for this date. Use update instead.",
                            HttpStatus.CONFLICT);
                });

        HealthLog healthLog = buildLog(request, user);
        healthLogRepository.save(healthLog);

        // Analyze for warnings
        List<HealthPatternAnalyzer.HealthWarning> warnings = patternAnalyzer.analyzeLog(healthLog);
        if (!warnings.isEmpty()) {
            try {
                kafkaTemplate.send("smartlife.health.alert", user.getId().toString(),
                        new HealthAlertEvent(user.getId(), healthLog.getLogDate(), warnings));
            } catch (Exception e) {
                log.warn("Kafka unavailable — health alert not published: {}", e.getMessage());
            }
            log.warn("Health warnings detected for user {}: {}", user.getEmail(), warnings.size());
        }

        return HealthLogDto.from(healthLog, warnings);
    }

    @Transactional
    public HealthLogDto updateLog(UUID logId, HealthLogRequest request, User user) {
        HealthLog existing = healthLogRepository.findByIdAndUserId(logId, user.getId())
                .orElseThrow(() -> new SmartLifeException("Health log not found", HttpStatus.NOT_FOUND));

        updateLogFields(existing, request);
        healthLogRepository.save(existing);

        List<HealthPatternAnalyzer.HealthWarning> warnings = patternAnalyzer.analyzeLog(existing);
        return HealthLogDto.from(existing, warnings);
    }

    @Transactional(readOnly = true)
    public Page<HealthLogDto> getLogs(UUID userId, Pageable pageable) {
        return healthLogRepository.findByUserIdOrderByLogDateDesc(userId, pageable)
                .map(l -> HealthLogDto.from(l, List.of()));
    }

    @Transactional(readOnly = true)
    public HealthInsightsDto getInsights(UUID userId, int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        List<HealthLog> logs = healthLogRepository.findByUserAndDateRange(userId, from, LocalDate.now());

        Double avgSleep = healthLogRepository.getAvgSleepHours(userId, from);
        Double avgMood = healthLogRepository.getAvgMoodScore(userId, from);
        Double avgWeight = healthLogRepository.getAvgWeight(userId, from);

        List<HealthPatternAnalyzer.HealthWarning> trendWarnings = patternAnalyzer.analyzeTrend(logs);

        return new HealthInsightsDto(days, logs.size(), avgSleep, avgMood, avgWeight, trendWarnings);
    }

    @Transactional(readOnly = true)
    public List<HealthLogDto> getDateRange(UUID userId, LocalDate from, LocalDate to) {
        return healthLogRepository.findByUserAndDateRange(userId, from, to)
                .stream().map(l -> HealthLogDto.from(l, List.of())).toList();
    }

    @Transactional
    public void deleteLog(UUID logId, UUID userId) {
        HealthLog log = healthLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new SmartLifeException("Health log not found", HttpStatus.NOT_FOUND));
        healthLogRepository.delete(log);
    }

    private HealthLog buildLog(HealthLogRequest r, User user) {
        return HealthLog.builder()
                .user(user)
                .logDate(r.logDate() != null ? r.logDate() : LocalDate.now())
                .systolicBp(r.systolicBp()).diastolicBp(r.diastolicBp())
                .bloodGlucose(r.bloodGlucose()).weight(r.weight()).temperature(r.temperature())
                .heartRate(r.heartRate()).sleepHours(r.sleepHours()).sleepQuality(r.sleepQuality())
                .energyLevel(r.energyLevel()).moodScore(r.moodScore()).stressLevel(r.stressLevel())
                .exerciseMinutes(r.exerciseMinutes()).exerciseType(r.exerciseType())
                .symptoms(r.symptoms()).medications(r.medications())
                .waterIntake(r.waterIntake()).notes(r.notes())
                .build();
    }

    private void updateLogFields(HealthLog log, HealthLogRequest r) {
        if (r.systolicBp() != null) log.setSystolicBp(r.systolicBp());
        if (r.diastolicBp() != null) log.setDiastolicBp(r.diastolicBp());
        if (r.bloodGlucose() != null) log.setBloodGlucose(r.bloodGlucose());
        if (r.weight() != null) log.setWeight(r.weight());
        if (r.temperature() != null) log.setTemperature(r.temperature());
        if (r.heartRate() != null) log.setHeartRate(r.heartRate());
        if (r.sleepHours() != null) log.setSleepHours(r.sleepHours());
        if (r.sleepQuality() != null) log.setSleepQuality(r.sleepQuality());
        if (r.moodScore() != null) log.setMoodScore(r.moodScore());
        if (r.stressLevel() != null) log.setStressLevel(r.stressLevel());
        if (r.exerciseMinutes() != null) log.setExerciseMinutes(r.exerciseMinutes());
        if (r.symptoms() != null) log.setSymptoms(r.symptoms());
        if (r.medications() != null) log.setMedications(r.medications());
        if (r.waterIntake() != null) log.setWaterIntake(r.waterIntake());
        if (r.notes() != null) log.setNotes(r.notes());
    }

    public record HealthAlertEvent(UUID userId, LocalDate date,
                                    List<HealthPatternAnalyzer.HealthWarning> warnings) {}
}
