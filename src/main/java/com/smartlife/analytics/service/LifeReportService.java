package com.smartlife.analytics.service;

import com.smartlife.analytics.dto.WeeklyReportDto;
import com.smartlife.document.repository.DocumentRepository;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.health.repository.HealthLogRepository;
import com.smartlife.health.service.HealthPatternAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LifeReportService {

    private final DocumentRepository documentRepository;
    private final ExpenseRepository expenseRepository;
    private final HealthLogRepository healthLogRepository;
    private final HealthPatternAnalyzer healthPatternAnalyzer;
    private final LifeScoreService lifeScoreService;

    private static final List<String> WEEKLY_TIPS = List.of(
            "Upload your documents digitally — never miss an expiry again.",
            "Scan grocery receipts to auto-track your food spending.",
            "Log your weight weekly to spot health trends early.",
            "Set a monthly budget for dining out — it's usually the #1 overspend category.",
            "Review your subscriptions monthly — cancel what you don't use.",
            "Drink 8 glasses of water daily and log it — it correlates with higher energy scores.",
            "A 30-minute walk 5x per week significantly improves mood scores.",
            "Keep your passport and ID documents updated — set reminders 6 months before expiry."
    );

    @Transactional(readOnly = true)
    public WeeklyReportDto generateWeeklyReport(UUID userId) {
        LocalDate today     = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate lastWeekStart = weekStart.minusDays(7);
        LocalDate lastWeekEnd   = weekStart.minusDays(1);

        // Expenses
        BigDecimal thisWeekExpenses = orZero(
                expenseRepository.getTotalByUserAndDateRange(userId, weekStart, today));
        BigDecimal lastWeekExpenses = orZero(
                expenseRepository.getTotalByUserAndDateRange(userId, lastWeekStart, lastWeekEnd));
        double expenseChange = lastWeekExpenses.doubleValue() > 0
                ? ((thisWeekExpenses.doubleValue() - lastWeekExpenses.doubleValue())
                        / lastWeekExpenses.doubleValue()) * 100
                : 0;

        String topCategory = expenseRepository.getCategoryTotals(userId, weekStart, today)
                .stream().findFirst()
                .map(ct -> ct.getCategory().name().replace("_", " ").toLowerCase())
                .orElse("none");

        // Health
        var healthLogs = healthLogRepository.findByUserAndDateRange(userId, weekStart, today);
        double avgSleep = healthLogs.stream()
                .filter(l -> l.getSleepHours() != null)
                .mapToDouble(l -> l.getSleepHours()).average().orElse(0);
        double avgMood = healthLogs.stream()
                .filter(l -> l.getMoodScore() != null)
                .mapToDouble(l -> l.getMoodScore()).average().orElse(0);

        var trendWarnings = healthPatternAnalyzer.analyzeTrend(healthLogs);
        List<String> healthAlerts = trendWarnings.stream().map(HealthPatternAnalyzer.HealthWarning::title).toList();

        // Documents
        long newDocs = documentRepository.findByUserId(userId,
                org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(d -> !d.getUploadedAt().toLocalDate().isBefore(weekStart)).count();
        int expiringSoon = documentRepository.findExpiringDocuments(userId, today, today.plusDays(30)).size();

        // Life score
        var lifeScore = lifeScoreService.computeScore(userId);

        // Pick a rotating weekly tip
        String tip = WEEKLY_TIPS.get((int) (today.getDayOfYear() % WEEKLY_TIPS.size()));

        return new WeeklyReportDto(
                weekStart, today,
                (int) newDocs, expiringSoon,
                thisWeekExpenses, lastWeekExpenses,
                Math.round(expenseChange * 10.0) / 10.0,
                topCategory,
                Math.round(avgSleep * 10.0) / 10.0,
                Math.round(avgMood * 10.0) / 10.0,
                healthLogs.size(), healthAlerts,
                0,   // remindersTriggered — fetch from automation if needed
                lifeScore,
                List.of(tip)
        );
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
