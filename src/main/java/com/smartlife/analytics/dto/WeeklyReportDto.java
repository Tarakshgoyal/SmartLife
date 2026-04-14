package com.smartlife.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklyReportDto(
        LocalDate weekStart,
        LocalDate weekEnd,

        // Documents
        int newDocumentsUploaded,
        int documentsExpiringSoon,

        // Expenses
        BigDecimal totalExpenses,
        BigDecimal previousWeekExpenses,
        double expenseChangePercent,
        String topSpendingCategory,

        // Health
        double avgSleepHours,
        double avgMoodScore,
        int healthLogsDays,
        List<String> healthAlerts,

        // Reminders
        int remindersTriggered,

        // Life score
        LifeScoreDto lifeScore,

        // Tips
        List<String> weeklyTips
) {
    public String toSummaryText() {
        return String.format("""
                📊 Your Week (%s to %s)

                💰 Expenses: ₹%.2f (%s%.1f%% vs last week)
                😴 Avg Sleep: %.1fh | 😊 Mood: %.1f/10
                📄 New documents: %d | ⚠️ Expiring soon: %d
                🏆 Life Score: %d/100 (%s)

                Top tip: %s
                """,
                weekStart, weekEnd,
                totalExpenses,
                expenseChangePercent >= 0 ? "+" : "",
                expenseChangePercent,
                avgSleepHours, avgMoodScore,
                newDocumentsUploaded, documentsExpiringSoon,
                lifeScore.overallScore(), lifeScore.grade(),
                weeklyTips.isEmpty() ? "Keep tracking your health daily!" : weeklyTips.get(0)
        );
    }
}
