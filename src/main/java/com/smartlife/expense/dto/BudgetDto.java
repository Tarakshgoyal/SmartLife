package com.smartlife.expense.dto;

import com.smartlife.expense.model.Budget;
import com.smartlife.expense.model.ExpenseCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetDto(
        UUID id,
        ExpenseCategory category,
        BigDecimal limitAmount,
        String monthYear
) {
    public static BudgetDto from(Budget b) {
        return new BudgetDto(b.getId(), b.getCategory(), b.getLimitAmount(), b.getMonthYear());
    }
}
