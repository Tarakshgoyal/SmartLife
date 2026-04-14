package com.smartlife.analytics.dto;

import com.smartlife.automation.service.NotificationService;

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
        List<NotificationService.Notification> recentNotifications
) {
    public record ExpiringDocumentSummary(String title, String type, String expiryDate, int daysLeft) {}
}
