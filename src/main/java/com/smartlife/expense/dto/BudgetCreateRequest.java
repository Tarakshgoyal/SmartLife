package com.smartlife.expense.dto;

import com.smartlife.expense.model.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BudgetCreateRequest(
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin("1") BigDecimal limitAmount,
        String monthYear
) {}
