package com.smartlife.analytics.dto;

import com.smartlife.automation.service.NotificationService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardDto(
        // User summary
        String userFullName,

        // Document stats
        long totalDocuments,
        long documentsExpiringSoon,
        List<ExpiringDocumentSummary> expiringDocuments,

        // Expense stats
        BigDecimal currentMonthSpend,
        BigDecimal lastMonthSpend,
        Map<String, BigDecimal> topSpendingCategories,
        long anomalyCount,

        // Health stats
        Double avgSleepHours7Days,
        Double avgMoodScore7Days,
        long healthLogsThisMonth,

        // Pending reminders
        long pendingReminders,

        // Unread notifications
        List<NotificationService.Notification> recentNotifications,

        // AI daily briefing (Llama 3.2)
        @Schema(description = "AI-generated daily briefing summarising all life areas (Llama 3.2)")
        String aiDailyBriefing
) {
    // Backwards-compat constructor without aiDailyBriefing
    public DashboardDto(String userFullName, long totalDocuments, long documentsExpiringSoon,
                        List<ExpiringDocumentSummary> expiringDocuments,
                        BigDecimal currentMonthSpend, BigDecimal lastMonthSpend,
                        Map<String, BigDecimal> topSpendingCategories, long anomalyCount,
                        Double avgSleepHours7Days, Double avgMoodScore7Days, long healthLogsThisMonth,
                        long pendingReminders, List<NotificationService.Notification> recentNotifications) {
        this(userFullName, totalDocuments, documentsExpiringSoon, expiringDocuments,
             currentMonthSpend, lastMonthSpend, topSpendingCategories, anomalyCount,
             avgSleepHours7Days, avgMoodScore7Days, healthLogsThisMonth,
             pendingReminders, recentNotifications, null);
    }

    public record ExpiringDocumentSummary(String title, String type, String expiryDate, int daysLeft) {}
}
