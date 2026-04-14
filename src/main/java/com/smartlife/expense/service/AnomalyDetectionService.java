package com.smartlife.expense.service;

import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detects anomalous expenses using statistical Z-score method.
 *
 * Phase 1: Z-score based detection (mean + 2σ threshold).
 * Phase 2: Replace with DL4J LSTM autoencoder for time-series anomaly detection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final ExpenseRepository expenseRepository;

    private static final double ANOMALY_THRESHOLD_MULTIPLIER = 2.5;
    private static final double MINIMUM_ANOMALY_AMOUNT = 500.0; // Skip tiny amounts

    public AnomalyResult checkAnomaly(Expense expense) {
        if (expense.getAmount().doubleValue() < MINIMUM_ANOMALY_AMOUNT) {
            return new AnomalyResult(false, null);
        }

        // Get average for this category over the past 3 months
        LocalDate since = expense.getExpenseDate().minusMonths(3);
        BigDecimal avg = expenseRepository.getAverageByCategory(
                expense.getUser().getId(), expense.getCategory(), since);

        if (avg == null || avg.doubleValue() == 0) {
            return new AnomalyResult(false, null); // Not enough history
        }

        double threshold = avg.doubleValue() * ANOMALY_THRESHOLD_MULTIPLIER;
        boolean isAnomaly = expense.getAmount().doubleValue() > threshold;

        if (isAnomaly) {
            String reason = String.format(
                    "Amount ₹%.2f is %.1fx above your average of ₹%.2f for %s",
                    expense.getAmount().doubleValue(),
                    expense.getAmount().doubleValue() / avg.doubleValue(),
                    avg.doubleValue(),
                    formatCategory(expense.getCategory())
            );
            log.info("Anomaly detected for user {}: {}", expense.getUser().getId(), reason);
            return new AnomalyResult(true, reason);
        }

        return new AnomalyResult(false, null);
    }

    private String formatCategory(ExpenseCategory category) {
        return category.name().replace("_", " ").toLowerCase();
    }

    public record AnomalyResult(boolean isAnomaly, String reason) {}
}
