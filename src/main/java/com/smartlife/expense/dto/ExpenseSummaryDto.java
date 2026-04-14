package com.smartlife.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ExpenseSummaryDto(
        String monthYear,
        BigDecimal totalSpent,
        Map<String, BigDecimal> categoryBreakdown,
        List<BudgetStatusDto> budgetStatuses
) {}
