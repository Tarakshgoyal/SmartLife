package com.smartlife.analytics.service;

import com.smartlife.analytics.dto.DashboardDto;
import com.smartlife.auth.model.User;
import com.smartlife.automation.repository.ReminderRepository;
import com.smartlife.automation.service.NotificationService;
import com.smartlife.config.OllamaService;
import com.smartlife.document.repository.DocumentRepository;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.health.model.HealthLog;
import com.smartlife.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final DocumentRepository documentRepository;
    private final ExpenseRepository expenseRepository;
    private final HealthLogRepository healthLogRepository;
    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;

    @Lazy
    @Autowired
    private OllamaService ollamaService;

    @Transactional(readOnly = true)
    @Cacheable(value = "dashboard", key = "#user.id", unless = "#result == null")
    public DashboardDto getDashboard(User user) {
        LocalDate now = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);

        // Documents
        long totalDocs = documentRepository.findByUserId(user.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();

        List<com.smartlife.document.model.Document> expiring =
                documentRepository.findExpiringDocuments(user.getId(), now, now.plusDays(30));

        List<DashboardDto.ExpiringDocumentSummary> expiringSummaries = expiring.stream()
                .map(d -> new DashboardDto.ExpiringDocumentSummary(
                        d.getTitle() != null ? d.getTitle() : d.getFileName(),
                        d.getType().name(),
                        d.getExpiryDate().toString(),
                        (int) (d.getExpiryDate().toEpochDay() - now.toEpochDay())
                ))
                .toList();

        // Expenses
        BigDecimal thisMonthSpend = expenseRepository.getTotalByUserAndDateRange(
                user.getId(), thisMonth.atDay(1), thisMonth.atEndOfMonth());

        BigDecimal lastMonthSpend = expenseRepository.getTotalByUserAndDateRange(
                user.getId(), lastMonth.atDay(1), lastMonth.atEndOfMonth());

        List<ExpenseRepository.CategoryTotal> categoryTotals = expenseRepository.getCategoryTotals(
                user.getId(), thisMonth.atDay(1), thisMonth.atEndOfMonth());

        Map<String, BigDecimal> topCategories = categoryTotals.stream()
                .limit(5)
                .collect(Collectors.toMap(
                        ct -> ct.getCategory().name(),
                        ExpenseRepository.CategoryTotal::getTotal
                ));

        long anomalyCount = expenseRepository.findAnomalies(user.getId()).size();

        // Health
        LocalDate sevenDaysAgo = now.minusDays(7);
        Double avgSleep = healthLogRepository.getAvgSleepHours(user.getId(), sevenDaysAgo);
        Double avgMood = healthLogRepository.getAvgMoodScore(user.getId(), sevenDaysAgo);
        long healthLogsThisMonth = healthLogRepository
                .findByUserAndDateRange(user.getId(), thisMonth.atDay(1), thisMonth.atEndOfMonth())
                .size();

        // Reminders
        long pendingReminders = reminderRepository
                .findByUserIdAndSentFalseOrderByScheduledAtAsc(user.getId()).size();

        // Notifications
        List<NotificationService.Notification> recentNotifications =
                notificationService.getNotifications(user.getId()).stream().limit(5).toList();

        // ── Llama 3.2 daily briefing ─────────────────────────────────────────
        String aiDailyBriefing = null;
        try {
            String briefingPrompt = "User: " + user.getFullName() + "\n" +
                "Today's date: " + now + "\n" +
                "Spending this month: Rs." + (thisMonthSpend != null ? thisMonthSpend : 0) +
                " (last month: Rs." + (lastMonthSpend != null ? lastMonthSpend : 0) + ")\n" +
                "Anomalous expenses: " + anomalyCount + "\n" +
                "Avg sleep (7 days): " + (avgSleep != null ? String.format("%.1f", avgSleep) + "h" : "no data") + "\n" +
                "Avg mood (7 days): " + (avgMood != null ? String.format("%.1f", avgMood) + "/10" : "no data") + "\n" +
                "Documents expiring soon: " + expiringSummaries.size() + "\n" +
                "Pending reminders: " + pendingReminders;

            aiDailyBriefing = ollamaService.generate(
                "You are a personal AI life assistant. Write a warm, concise daily briefing (3-4 sentences) " +
                "for the user based on their life data. Highlight the most important thing to focus on today.",
                briefingPrompt
            );
        } catch (Exception e) {
            log.debug("AI dashboard briefing skipped: {}", e.getMessage());
        }

        return new DashboardDto(
                user.getFullName(),
                totalDocs, expiringSummaries.size(), expiringSummaries,
                thisMonthSpend != null ? thisMonthSpend : BigDecimal.ZERO,
                lastMonthSpend != null ? lastMonthSpend : BigDecimal.ZERO,
                topCategories, anomalyCount,
                avgSleep, avgMood, healthLogsThisMonth,
                pendingReminders, recentNotifications, aiDailyBriefing
        );
    }
}
