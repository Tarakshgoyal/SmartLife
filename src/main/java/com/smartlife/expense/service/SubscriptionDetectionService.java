package com.smartlife.expense.service;

import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.Subscription;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.expense.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Detects recurring subscription charges from a user's expense history.
 *
 * Algorithm:
 *  1. Group expenses by merchant name (case-insensitive).
 *  2. For each merchant with ≥2 charges, compute pairwise gaps (days).
 *  3. If the median gap clusters around 7, 30, or 365 days → flag as subscription.
 *  4. Persist/update Subscription entities; compute next expected charge date.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionDetectionService {

    private static final int GAP_TOLERANCE_DAYS = 5;
    private static final int WEEKLY = 7;
    private static final int MONTHLY = 30;
    private static final int YEARLY = 365;

    private final ExpenseRepository expenseRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Async
    @Transactional
    public void detectSubscriptions(UUID userId) {
        LocalDate since = LocalDate.now().minusMonths(13);
        List<Expense> expenses = expenseRepository.findByUserAndDateRange(userId, since, LocalDate.now());

        // Group by normalised merchant name
        Map<String, List<Expense>> byMerchant = new LinkedHashMap<>();
        for (Expense e : expenses) {
            if (e.getMerchant() == null || e.getMerchant().isBlank()) continue;
            String key = e.getMerchant().trim().toLowerCase();
            byMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Expense>> entry : byMerchant.entrySet()) {
            List<Expense> list = entry.getValue();
            if (list.size() < 2) continue;

            list.sort(Comparator.comparing(Expense::getExpenseDate));
            List<Long> gaps = computeGaps(list);
            String frequency = detectFrequency(gaps);
            if (frequency == null) continue;

            // Verify amounts are approximately equal (within 10%)
            if (!amountsConsistent(list)) continue;

            BigDecimal avgAmount = averageAmount(list);
            String merchantDisplay = list.get(0).getMerchant();

            Subscription sub = subscriptionRepository
                    .findByUserIdAndNameIgnoreCase(userId, merchantDisplay)
                    .orElse(Subscription.builder()
                            .user(list.get(0).getUser())
                            .name(merchantDisplay)
                            .firstChargeDate(list.get(0).getExpenseDate())
                            .build());

            sub.setAmount(avgAmount);
            sub.setFrequency(frequency);
            sub.setLastChargeDate(list.get(list.size() - 1).getExpenseDate());
            sub.setNextExpectedDate(computeNextDate(sub.getLastChargeDate(), frequency));
            sub.setChargeCount(list.size());
            sub.setActive(true);
            subscriptionRepository.save(sub);
            log.info("Subscription detected: {} {} — {} (next: {})",
                    frequency, merchantDisplay, avgAmount, sub.getNextExpectedDate());
        }
    }

    @Transactional(readOnly = true)
    public List<Subscription> getActiveSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserIdAndActiveTrue(userId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> getUpcomingRenewals(UUID userId, int daysAhead) {
        return subscriptionRepository.findUpcomingRenewals(
                userId, LocalDate.now(), LocalDate.now().plusDays(daysAhead));
    }

    @Transactional
    public void markUnused(UUID subscriptionId, UUID userId) {
        subscriptionRepository.findById(subscriptionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .ifPresent(s -> {
                    s.setMarkedUnused(true);
                    subscriptionRepository.save(s);
                });
    }

    // ── Detection helpers ─────────────────────────────────────────────────────

    private List<Long> computeGaps(List<Expense> sorted) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getExpenseDate(),
                    sorted.get(i).getExpenseDate()));
        }
        return gaps;
    }

    private String detectFrequency(List<Long> gaps) {
        if (gaps.isEmpty()) return null;
        long median = median(gaps);

        if (Math.abs(median - WEEKLY)  <= GAP_TOLERANCE_DAYS) return "WEEKLY";
        if (Math.abs(median - MONTHLY) <= GAP_TOLERANCE_DAYS) return "MONTHLY";
        if (Math.abs(median - YEARLY)  <= GAP_TOLERANCE_DAYS * 3) return "YEARLY";
        return null;
    }

    private boolean amountsConsistent(List<Expense> expenses) {
        BigDecimal avg = averageAmount(expenses);
        if (avg.doubleValue() == 0) return false;
        return expenses.stream().allMatch(e -> {
            double ratio = e.getAmount().doubleValue() / avg.doubleValue();
            return ratio >= 0.85 && ratio <= 1.15;
        });
    }

    private BigDecimal averageAmount(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
    }

    private LocalDate computeNextDate(LocalDate last, String frequency) {
        return switch (frequency) {
            case "WEEKLY"  -> last.plusWeeks(1);
            case "YEARLY"  -> last.plusYears(1);
            default        -> last.plusMonths(1);  // MONTHLY
        };
    }

    private long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
