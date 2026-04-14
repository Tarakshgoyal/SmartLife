package com.smartlife.health;

import com.smartlife.health.model.HealthLog;
import com.smartlife.health.service.HealthPatternAnalyzer;
import com.smartlife.health.service.HealthPatternAnalyzer.WarningLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Health Pattern Analyzer")
class HealthPatternAnalyzerTest {

    private HealthPatternAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new HealthPatternAnalyzer();
    }

    @Test
    void highBloodPressureTriggersHighWarning() {
        HealthLog log = new HealthLog();
        log.setSystolicBp(150);
        log.setDiastolicBp(95);

        var warnings = analyzer.analyzeLog(log);

        assertThat(warnings).anyMatch(w ->
                w.level() == WarningLevel.HIGH && w.title().contains("Blood Pressure"));
    }

    @Test
    void normalVitalsProduceNoWarnings() {
        HealthLog log = new HealthLog();
        log.setSystolicBp(118);
        log.setDiastolicBp(76);
        log.setBloodGlucose(90.0);
        log.setSleepHours(7.5);
        log.setHeartRate(72);
        log.setTemperature(36.8);

        var warnings = analyzer.analyzeLog(log);
        assertThat(warnings).isEmpty();
    }

    @Test
    void criticalGlucoseTriggersHighWarning() {
        HealthLog log = new HealthLog();
        log.setBloodGlucose(250.0);

        var warnings = analyzer.analyzeLog(log);
        assertThat(warnings).anyMatch(w ->
                w.level() == WarningLevel.HIGH && w.title().contains("Blood Glucose"));
    }

    @Test
    void insufficientSleepTriggersMediumOrLow() {
        HealthLog log = new HealthLog();
        log.setSleepHours(4.5);

        var warnings = analyzer.analyzeLog(log);
        assertThat(warnings).anyMatch(w -> w.title().contains("Sleep"));
    }

    @Test
    void consecutivePoorSleepTriggersTrendWarning() {
        List<HealthLog> logs = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> {
                    HealthLog l = new HealthLog();
                    l.setSleepHours(4.0);
                    return l;
                })
                .toList();

        var warnings = analyzer.analyzeTrend(logs);
        assertThat(warnings).anyMatch(w -> w.title().contains("Sleep"));
    }

    @Test
    void highStressAndLowMoodTriggerWarning() {
        HealthLog log = new HealthLog();
        log.setMoodScore(2);
        log.setStressLevel(9);

        var warnings = analyzer.analyzeLog(log);
        assertThat(warnings).anyMatch(w -> w.title().contains("Stress") || w.title().contains("Mood"));
    }
}
