package com.smartlife.expense.controller;

import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import com.smartlife.expense.dto.*;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.service.ExpenseService;
import com.smartlife.expense.service.SpendingPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Expense tracking, budgets, anomaly detection, and spending predictions")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final SpendingPredictionService predictionService;

    @Operation(summary = "Create a new expense")
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseDto>> create(
            @Valid @RequestBody ExpenseCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(expenseService.createExpense(request, user), "Expense added"));
    }

    @Operation(summary = "List expenses (optionally filtered by category)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExpenseDto>>> getAll(
            @RequestParam(required = false) ExpenseCategory category,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.getUserExpenses(user.getId(), category, pageable)));
    }

    @Operation(summary = "Get monthly expense summary", description = "monthYear format: yyyy-MM (e.g. 2025-03)")
    @GetMapping("/summary/{monthYear}")
    public ResponseEntity<ApiResponse<ExpenseSummaryDto>> getSummary(
            @Parameter(description = "Month in yyyy-MM format") @PathVariable String monthYear,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.getMonthlySummary(user.getId(), monthYear)));
    }

    @Operation(summary = "Get current month expense summary")
    @GetMapping("/summary/current")
    public ResponseEntity<ApiResponse<ExpenseSummaryDto>> getCurrentMonthSummary(
            @AuthenticationPrincipal User user) {
        String current = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.getMonthlySummary(user.getId(), current)));
    }

    @Operation(summary = "Get ML-detected anomalous expenses")
    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<ExpenseDto>>> getAnomalies(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.getAnomalies(user.getId()),
                "Anomalous expenses detected"));
    }

    @Operation(summary = "Delete an expense")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        expenseService.deleteExpense(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Expense deleted"));
    }

    @Operation(summary = "Predict next month's spending using ML model")
    @GetMapping("/predict")
    public ResponseEntity<ApiResponse<SpendingPredictionDto>> predictNextMonth(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                predictionService.predictNextMonth(user.getId()),
                "Next month spending prediction"));
    }

    // ===== BUDGET ENDPOINTS =====
    @Operation(summary = "Set or update a monthly budget for a category")
    @PostMapping("/budgets")
    public ResponseEntity<ApiResponse<BudgetDto>> setBudget(
            @Valid @RequestBody BudgetCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(expenseService.setBudget(request, user), "Budget set"));
    }

    @Operation(summary = "Get budgets for a specific month", description = "monthYear format: yyyy-MM")
    @GetMapping("/budgets/{monthYear}")
    public ResponseEntity<ApiResponse<List<BudgetDto>>> getBudgets(
            @PathVariable String monthYear,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.getBudgets(user.getId(), monthYear)));
    }
}
