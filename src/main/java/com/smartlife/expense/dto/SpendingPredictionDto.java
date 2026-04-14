package com.smartlife.expense.dto;

import com.smartlife.expense.model.ExpenseCategory;

import java.math.BigDecimal;
import java.util.Map;

public record SpendingPredictionDto(
        String targetMonthYear,
        BigDecimal predictedTotal,
        Map<ExpenseCategory, BigDecimal> predictedByCategory,
        BigDecimal previousMonthActual,
        double changePercent,
        String modelType,
        boolean isSufficientData
) {}
