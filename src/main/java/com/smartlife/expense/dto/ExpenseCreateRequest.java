package com.smartlife.expense.dto;

import com.smartlife.expense.model.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCreateRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String description,
        String merchant,
        ExpenseCategory category,
        LocalDate expenseDate,
        Boolean isRecurring,
        String recurringInterval
) {}
