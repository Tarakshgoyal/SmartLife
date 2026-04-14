package com.smartlife.health.dto;

import com.smartlife.health.service.HealthPatternAnalyzer;

import java.util.List;

public record HealthInsightsDto(
        int periodDays,
        int logsCount,
        Double avgSleepHours,
        Double avgMoodScore,
        Double avgWeight,
        List<HealthPatternAnalyzer.HealthWarning> warnings
) {}
