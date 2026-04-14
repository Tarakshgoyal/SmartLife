package com.smartlife.expense.controller;

import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import com.smartlife.expense.dto.ExpenseCreateRequest;
import com.smartlife.expense.dto.ExpenseDto;
import com.smartlife.expense.dto.ReceiptScanResultDto;
import com.smartlife.expense.service.ExpenseService;
import com.smartlife.expense.service.ReceiptScanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/expenses/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipts", description = "OCR-based receipt scanning and automatic expense creation")
public class ReceiptController {

    private final ReceiptScanningService receiptScanningService;
    private final ExpenseService expenseService;

    /**
     * Scan a receipt image/PDF and return extracted data for user review.
     * The client can then confirm and POST to /expenses with the pre-filled fields.
     */
    @Operation(summary = "Scan a receipt image/PDF and return extracted data")
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReceiptScanResultDto>> scan(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {

        ReceiptScanResultDto result = receiptScanningService.scanReceipt(file);
        return ResponseEntity.ok(ApiResponse.success(result, "Receipt scanned successfully"));
    }

    /**
     * Scan receipt AND immediately create the expense record.
     */
    @Operation(summary = "Scan receipt and immediately create an expense record")
    @PostMapping(value = "/scan-and-save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExpenseDto>> scanAndSave(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) throws IOException {

        ReceiptScanResultDto result = receiptScanningService.scanReceipt(file);

        ExpenseCreateRequest req = new ExpenseCreateRequest(
                result.totalAmount(),
                result.merchant() != null ? result.merchant() : "Receipt expense",
                result.merchant(),
                result.suggestedCategory(),
                result.purchaseDate() != null ? result.purchaseDate() : LocalDate.now(),
                false,
                null
        );

        ExpenseDto expense = expenseService.createExpense(req, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(expense, "Expense created from receipt"));
    }
}
