package com.smartlife.expense.service;

import com.smartlife.expense.dto.SpendingPredictionDto;
import com.smartlife.expense.ml.SpendingPredictionModel;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Orchestrates monthly spending predictions using the DL4J LSTM model.
 *
 * Feature vector per month (15 features):
 *  [0]  total spend
 *  [1]  GROCERIES
 *  [2]  DINING
 *  [3]  TRANSPORT
 *  [4]  UTILITIES
 *  [5]  HEALTHCARE
 *  [6]  ENTERTAINMENT
 *  [7]  SHOPPING
 *  [8]  EDUCATION
 *  [9]  RENT
 *  [10] INSURANCE
 *  [11] INVESTMENT
 *  [12] SUBSCRIPTION
 *  [13] TRAVEL
 *  [14] OTHER
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingPredictionService {

    private static final ExpenseCategory[] CAT_ORDER = ExpenseCategory.values(); // 14 categories + index 0 = total
    private static final int MONTHS_HISTORY = 12;

    private final ExpenseRepository expenseRepository;
    private final SpendingPredictionModel predictionModel;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public SpendingPredictionDto predictNextMonth(UUID userId) {
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        YearMonth thisMonth = YearMonth.now();

        List<double[]> series = buildMonthlyFeatureSeries(userId, MONTHS_HISTORY);

        BigDecimal prevTotal = expenseRepository.getTotalByUserAndDateRange(
                userId, thisMonth.atDay(1), thisMonth.atEndOfMonth());

        // Try LSTM prediction
        var lstm = predictionModel.predict(series);
        if (lstm.isPresent()) {
            BigDecimal predicted = BigDecimal.valueOf(lstm.getAsDouble()).setScale(2, RoundingMode.HALF_UP);
            double changePercent = prevTotal != null && prevTotal.doubleValue() > 0
                    ? ((predicted.doubleValue() - prevTotal.doubleValue()) / prevTotal.doubleValue()) * 100
                    : 0.0;

            return new SpendingPredictionDto(
                    nextMonth.format(FMT), predicted,
                    predictByCategory(userId, nextMonth),
                    prevTotal != null ? prevTotal : BigDecimal.ZERO,
                    changePercent, "DL4J-LSTM", true
            );
        }

        // Fallback: weighted moving average (last 3 months)
        return movingAverageFallback(userId, nextMonth, prevTotal, series);
    }

    /** Trigger async LSTM retraining when new month data is available. */
    @Async
    public void retrainIfReady(UUID userId) {
        List<double[]> series = buildMonthlyFeatureSeries(userId, 24);
        if (series.size() >= 7) {
            log.info("Triggering LSTM retraining for user {}", userId);
            predictionModel.train(series);
        }
    }

    // ── Feature building ──────────────────────────────────────────────────────

    List<double[]> buildMonthlyFeatureSeries(UUID userId, int monthsBack) {
        List<double[]> series = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = monthsBack - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to   = ym.atEndOfMonth();

            BigDecimal total = expenseRepository.getTotalByUserAndDateRange(userId, from, to);
            if (total == null || total.doubleValue() == 0) continue; // skip empty months

            List<ExpenseRepository.CategoryTotal> cats =
                    expenseRepository.getCategoryTotals(userId, from, to);

            Map<ExpenseCategory, Double> catMap = new HashMap<>();
            for (var ct : cats) catMap.put(ct.getCategory(), ct.getTotal().doubleValue());

            double[] features = new double[15];
            features[0] = total.doubleValue();
            for (int j = 0; j < CAT_ORDER.length && j < 14; j++) {
                features[j + 1] = catMap.getOrDefault(CAT_ORDER[j], 0.0);
            }
            series.add(features);
        }
        return series;
    }

    private Map<ExpenseCategory, BigDecimal> predictByCategory(UUID userId, YearMonth target) {
        Map<ExpenseCategory, BigDecimal> result = new EnumMap<>(ExpenseCategory.class);
        YearMonth prev = target.minusMonths(1);

        List<ExpenseRepository.CategoryTotal> prevCats = expenseRepository.getCategoryTotals(
                userId, prev.atDay(1), prev.atEndOfMonth());

        // Simple linear projection per category (10% growth adjustment)
        for (var ct : prevCats) {
            result.put(ct.getCategory(), ct.getTotal().multiply(BigDecimal.valueOf(1.05))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private SpendingPredictionDto movingAverageFallback(UUID userId, YearMonth next,
                                                          BigDecimal prevTotal,
                                                          List<double[]> series) {
        if (series.isEmpty()) {
            return new SpendingPredictionDto(next.format(FMT), BigDecimal.ZERO,
                    Map.of(), BigDecimal.ZERO, 0, "INSUFFICIENT_DATA", false);
        }

        int window = Math.min(3, series.size());
        double avgTotal = series.subList(series.size() - window, series.size())
                .stream().mapToDouble(f -> f[0]).average().orElse(0);

        BigDecimal predicted = BigDecimal.valueOf(avgTotal).setScale(2, RoundingMode.HALF_UP);
        double changePercent = prevTotal != null && prevTotal.doubleValue() > 0
                ? ((predicted.doubleValue() - prevTotal.doubleValue()) / prevTotal.doubleValue()) * 100 : 0;

        return new SpendingPredictionDto(next.format(FMT), predicted,
                Map.of(), prevTotal != null ? prevTotal : BigDecimal.ZERO,
                changePercent, "MOVING_AVERAGE", series.size() >= 3);
    }
}
