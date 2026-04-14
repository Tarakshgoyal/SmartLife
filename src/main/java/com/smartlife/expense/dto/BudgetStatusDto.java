package com.smartlife.expense.dto;

import com.smartlife.expense.model.ExpenseCategory;

import java.math.BigDecimal;

public record BudgetStatusDto(
        ExpenseCategory category,
        BigDecimal limit,
        BigDecimal spent,
        double percentageUsed
) {}
