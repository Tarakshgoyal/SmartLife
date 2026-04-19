package com.smartlife.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record LifeScoreDto(
        int overallScore,
        Map<String, Integer> breakdown,
        String grade,
        String trend,
        List<String> topRecommendations,

        @Schema(description = "AI-generated life coaching summary (Llama 3.2)")
        String aiCoachingSummary
) {
    // Backwards-compat constructor without aiCoachingSummary
    public LifeScoreDto(int overallScore, Map<String, Integer> breakdown,
                        String grade, String trend, List<String> topRecommendations) {
        this(overallScore, breakdown, grade, trend, topRecommendations, null);
    }

    public static String gradeFor(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "F";
    }
}
