package com.smartlife.health.dto;

import com.smartlife.health.service.HealthPatternAnalyzer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record HealthInsightsDto(
        int periodDays,
        int logsCount,
        Double avgSleepHours,
        Double avgMoodScore,
        Double avgWeight,
        List<HealthPatternAnalyzer.HealthWarning> warnings,

        @Schema(description = "AI-generated health summary and personalised advice (Llama 3.2)")
        String aiSummary
) {
    // Backwards-compat constructor without aiSummary
    public HealthInsightsDto(int periodDays, int logsCount, Double avgSleepHours,
                              Double avgMoodScore, Double avgWeight,
                              List<HealthPatternAnalyzer.HealthWarning> warnings) {
        this(periodDays, logsCount, avgSleepHours, avgMoodScore, avgWeight, warnings, null);
    }
}
