package com.smartlife.analytics.dto;

import java.util.Map;

public record LifeScoreDto(
        int overallScore,           // 0–100
        Map<String, Integer> breakdown,  // each dimension 0–100
        String grade,               // A, B, C, D, F
        String trend,               // IMPROVING, STABLE, DECLINING
        java.util.List<String> topRecommendations
) {
    public static String gradeFor(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "F";
    }
}
