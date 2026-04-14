package com.smartlife.expense.dto;

import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseDto(
        UUID id,
        BigDecimal amount,
        String description,
        String merchant,
        ExpenseCategory category,
        LocalDate expenseDate,
        boolean isRecurring,
        String recurringInterval,
        boolean isAnomaly,
        String anomalyReason,
        String source,
        LocalDateTime createdAt
) {
    public static ExpenseDto from(Expense e) {
        return new ExpenseDto(e.getId(), e.getAmount(), e.getDescription(), e.getMerchant(),
                e.getCategory(), e.getExpenseDate(), e.isRecurring(), e.getRecurringInterval(),
                e.isAnomaly(), e.getAnomalyReason(), e.getSource(), e.getCreatedAt());
    }
}
