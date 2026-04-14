package com.smartlife.health.service;

import com.smartlife.health.model.HealthLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects health patterns and generates early warnings.
 *
 * Phase 1: Rule-based pattern detection with clinical thresholds.
 * Phase 2: DL4J LSTM model for time-series correlation detection
 *          (e.g., poor sleep → mood decline → headache correlation).
 */
@Service
@Slf4j
public class HealthPatternAnalyzer {

    // Clinical thresholds
    private static final double HIGH_BP_SYSTOLIC = 140.0;
    private static final double HIGH_BP_DIASTOLIC = 90.0;
    private static final double LOW_BP_SYSTOLIC = 90.0;
    private static final double HIGH_GLUCOSE = 200.0;
    private static final double PRE_DIABETIC_GLUCOSE = 140.0;
    private static final double MIN_SLEEP_HOURS = 6.0;
    private static final int HIGH_HEART_RATE = 100;
    private static final int HIGH_TEMP_CELSIUS = 38;

    public List<HealthWarning> analyzeLog(HealthLog log) {
        List<HealthWarning> warnings = new ArrayList<>();

        // Blood Pressure
        if (log.getSystolicBp() != null && log.getDiastolicBp() != null) {
            if (log.getSystolicBp() >= HIGH_BP_SYSTOLIC || log.getDiastolicBp() >= HIGH_BP_DIASTOLIC) {
                warnings.add(new HealthWarning(WarningLevel.HIGH,
                        "High Blood Pressure",
                        String.format("BP reading of %d/%d mmHg is above normal (120/80). " +
                                "Consider consulting a doctor.", log.getSystolicBp(), log.getDiastolicBp())));
            } else if (log.getSystolicBp() < LOW_BP_SYSTOLIC) {
                warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                        "Low Blood Pressure",
                        "Systolic BP is below 90 mmHg. Stay hydrated and rest."));
            }
        }

        // Blood Glucose
        if (log.getBloodGlucose() != null) {
            if (log.getBloodGlucose() >= HIGH_GLUCOSE) {
                warnings.add(new HealthWarning(WarningLevel.HIGH,
                        "High Blood Glucose",
                        String.format("Glucose reading of %.1f mg/dL is very high. Consult your doctor immediately.",
                                log.getBloodGlucose())));
            } else if (log.getBloodGlucose() >= PRE_DIABETIC_GLUCOSE) {
                warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                        "Elevated Blood Glucose",
                        String.format("Glucose of %.1f mg/dL is in pre-diabetic range. Monitor closely.",
                                log.getBloodGlucose())));
            }
        }

        // Sleep
        if (log.getSleepHours() != null && log.getSleepHours() < MIN_SLEEP_HOURS) {
            warnings.add(new HealthWarning(WarningLevel.LOW,
                    "Insufficient Sleep",
                    String.format("You slept %.1f hours. Aim for 7-9 hours for optimal health.",
                            log.getSleepHours())));
        }

        // Heart Rate
        if (log.getHeartRate() != null && log.getHeartRate() > HIGH_HEART_RATE) {
            warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                    "Elevated Heart Rate",
                    String.format("Resting heart rate of %d bpm is above normal (60-100).",
                            log.getHeartRate())));
        }

        // Temperature
        if (log.getTemperature() != null && log.getTemperature() >= HIGH_TEMP_CELSIUS) {
            warnings.add(new HealthWarning(WarningLevel.HIGH,
                    "Fever Detected",
                    String.format("Temperature of %.1f°C indicates fever. Rest and stay hydrated.",
                            log.getTemperature())));
        }

        // Low mood + high stress
        if (log.getMoodScore() != null && log.getStressLevel() != null
                && log.getMoodScore() <= 3 && log.getStressLevel() >= 8) {
            warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                    "High Stress / Low Mood",
                    "Your mood and stress indicators suggest you may be experiencing burnout. Consider relaxation techniques."));
        }

        return warnings;
    }

    public List<HealthWarning> analyzeTrend(List<HealthLog> recentLogs) {
        List<HealthWarning> warnings = new ArrayList<>();
        if (recentLogs.size() < 3) return warnings;

        // Check consecutive poor sleep
        long poorSleepDays = recentLogs.stream()
                .filter(l -> l.getSleepHours() != null && l.getSleepHours() < MIN_SLEEP_HOURS)
                .count();

        if (poorSleepDays >= 3) {
            warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                    "Chronic Sleep Deprivation",
                    String.format("You've had poor sleep for %d of the last %d days. " +
                            "This can affect immunity and mental health.", poorSleepDays, recentLogs.size())));
        }

        // Declining mood trend
        double avgMood = recentLogs.stream()
                .filter(l -> l.getMoodScore() != null)
                .mapToInt(l -> l.getMoodScore())
                .average().orElse(5.0);

        if (avgMood < 4.0) {
            warnings.add(new HealthWarning(WarningLevel.MEDIUM,
                    "Sustained Low Mood",
                    String.format("Your average mood score is %.1f/10 over the past week. " +
                            "Consider speaking with someone you trust.", avgMood)));
        }

        return warnings;
    }

    public enum WarningLevel { LOW, MEDIUM, HIGH }

    public record HealthWarning(WarningLevel level, String title, String message) {}
}
