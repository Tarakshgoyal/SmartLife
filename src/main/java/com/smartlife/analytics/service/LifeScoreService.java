package com.smartlife.analytics.service;

import com.smartlife.analytics.dto.LifeScoreDto;
import com.smartlife.config.OllamaService;
import com.smartlife.expense.repository.BudgetRepository;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Computes a composite "Life Score" (0–100) across four dimensions:
 *
 *  Financial Health (30%)  — budget adherence, anomaly rate, savings behaviour
 *  Physical Health  (30%)  — sleep hours, vitals, exercise consistency
 *  Mental Wellbeing (20%)  — mood score, stress level
 *  Life Organisation(20%)  — documents up to date, reminders fulfilled
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LifeScoreService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final HealthLogRepository healthLogRepository;

    @Lazy
    @Autowired
    private OllamaService ollamaService;

    @Transactional(readOnly = true)
    @Cacheable(value = "lifeScore", key = "#userId")
    public LifeScoreDto computeScore(UUID userId) {
        int financialScore  = computeFinancialScore(userId);
        int physicalScore   = computePhysicalScore(userId);
        int mentalScore     = computeMentalScore(userId);
        int organisationScore = computeOrganisationScore(userId);

        int overall = (int) Math.round(
                financialScore  * 0.30 +
                physicalScore   * 0.30 +
                mentalScore     * 0.20 +
                organisationScore * 0.20
        );

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("Financial Health",   financialScore);
        breakdown.put("Physical Health",    physicalScore);
        breakdown.put("Mental Wellbeing",   mentalScore);
        breakdown.put("Life Organisation",  organisationScore);

        List<String> recommendations = buildRecommendations(
                financialScore, physicalScore, mentalScore, organisationScore);

        // ── Llama 3.2 AI coaching summary ───────────────────────────────────
        String aiCoachingSummary = null;
        try {
            aiCoachingSummary = ollamaService.generate(
                "You are a personal life coach. Based on the user life score breakdown, provide a motivating 2-3 sentence summary with the most important advice. Be specific, warm, and actionable.",
                "Life score breakdown:\n" +
                "- Financial Health: " + financialScore + "/100\n" +
                "- Physical Health: " + physicalScore + "/100\n" +
                "- Mental Wellbeing: " + mentalScore + "/100\n" +
                "- Life Organisation: " + organisationScore + "/100\n" +
                "- Overall: " + overall + "/100 (Grade " + LifeScoreDto.gradeFor(overall) + ")\n" +
                "Rule-based recommendations: " + recommendations
            );
        } catch (Exception e) {
            log.debug("AI life score coaching skipped: {}", e.getMessage());
        }

        return new LifeScoreDto(overall, breakdown, LifeScoreDto.gradeFor(overall),
                "STABLE", recommendations, aiCoachingSummary);
    }

    // ── Dimension scorers ─────────────────────────────────────────────────────

    private int computeFinancialScore(UUID userId) {
        YearMonth thisMonth = YearMonth.now();
        String monthYear = thisMonth.toString().replace("-", "-");

        // Budget adherence: count of budgets where spending ≤ limit
        var budgets = budgetRepository.findByUserIdAndMonthYear(userId, thisMonth.toString());
        if (budgets.isEmpty()) return 50; // neutral if no budgets set

        LocalDate from = thisMonth.atDay(1);
        LocalDate to   = thisMonth.atEndOfMonth();
        var catTotals = expenseRepository.getCategoryTotals(userId, from, to);
        Map<String, java.math.BigDecimal> spending = new HashMap<>();
        for (var ct : catTotals) spending.put(ct.getCategory().name(), ct.getTotal());

        long within = budgets.stream().filter(b -> {
            var spent = spending.getOrDefault(b.getCategory().name(), java.math.BigDecimal.ZERO);
            return spent.compareTo(b.getLimitAmount()) <= 0;
        }).count();

        int adherenceScore = (int) ((within * 100.0) / budgets.size());

        // Anomaly penalty: -5 per anomaly
        int anomalyPenalty = Math.min(30, expenseRepository.findAnomalies(userId).size() * 5);

        return Math.max(0, Math.min(100, adherenceScore - anomalyPenalty));
    }

    private int computePhysicalScore(UUID userId) {
        LocalDate since = LocalDate.now().minusDays(14);

        Double avgSleep   = healthLogRepository.getAvgSleepHours(userId, since);
        Double avgWeight  = healthLogRepository.getAvgWeight(userId, since);

        int score = 60; // baseline

        // Sleep: ideal 7-9h. Penalty for under/over.
        if (avgSleep != null) {
            if (avgSleep >= 7 && avgSleep <= 9) score += 25;
            else if (avgSleep >= 6) score += 15;
            else score -= 10;
        }

        // Consistency: how many days logged in last 14 days
        var logs = healthLogRepository.findByUserAndDateRange(userId, since, LocalDate.now());
        int logDays = logs.size();
        score += Math.min(15, logDays); // up to +15 for daily logging

        return Math.max(0, Math.min(100, score));
    }

    private int computeMentalScore(UUID userId) {
        LocalDate since = LocalDate.now().minusDays(14);
        Double avgMood   = healthLogRepository.getAvgMoodScore(userId, since);

        if (avgMood == null) return 50;

        // 1–10 scale → 0–100 score, with 7+ giving full marks
        return (int) Math.min(100, (avgMood / 7.0) * 100);
    }

    private int computeOrganisationScore(UUID userId) {
        // Simple heuristic: based on how many health logs exist (tracking = organised)
        long totalLogs = healthLogRepository.countByUserId(userId);
        int base = totalLogs > 0 ? 60 : 30;
        return Math.min(100, base + (int) Math.min(40, totalLogs / 2));
    }

    // ── Recommendations ───────────────────────────────────────────────────────

    private List<String> buildRecommendations(int fin, int phy, int ment, int org) {
        List<String> recs = new ArrayList<>();

        if (fin < 60) recs.add("Set monthly budgets for your top spending categories to stay on track.");
        if (phy < 60) recs.add("Aim for 7–8 hours of sleep consistently — it improves every health marker.");
        if (ment < 60) recs.add("Consider daily mindfulness or journaling to improve your mood scores.");
        if (org < 60) recs.add("Log your health data daily for better insights and a higher life score.");

        if (recs.isEmpty()) recs.add("You're doing great! Keep maintaining your healthy habits.");
        return recs.stream().limit(3).toList();
    }
}
