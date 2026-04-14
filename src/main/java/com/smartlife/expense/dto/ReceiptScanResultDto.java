package com.smartlife.expense.dto;

import com.smartlife.expense.model.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptScanResultDto(
        String merchant,
        BigDecimal totalAmount,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        LocalDate purchaseDate,
        ExpenseCategory suggestedCategory,
        List<LineItem> lineItems,
        String rawText
) {
    public record LineItem(String name, int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {}
}
