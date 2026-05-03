package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.Budget;
import com.smartlife.model.Expense;
import com.smartlife.model.User;
import com.smartlife.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Expense>>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpenses(user.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> create(
            @AuthenticationPrincipal User user,
            @RequestBody Expense req) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.createExpense(user.getId(), req), "Created"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        expenseService.deleteExpense(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    @GetMapping("/budgets")
    public ResponseEntity<ApiResponse<List<Budget>>> getBudgets(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getBudgets(user.getId())));
    }

    @GetMapping("/budgets/{monthYear}")
    public ResponseEntity<ApiResponse<List<Budget>>> getBudgetsByMonth(
            @AuthenticationPrincipal User user,
            @PathVariable String monthYear) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getBudgetsByMonthYear(user.getId(), monthYear)));
    }

    @PostMapping("/budgets")
    public ResponseEntity<ApiResponse<Budget>> createBudget(
            @AuthenticationPrincipal User user,
            @RequestBody Budget req) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.createBudget(user.getId(), req), "Created"));
    }

    @GetMapping("/summary/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> currentSummary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getCurrentSummary(user.getId())));
    }

    @GetMapping("/summary/{monthYear}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summaryByMonth(
            @AuthenticationPrincipal User user,
            @PathVariable String monthYear) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getSummary(user.getId(), monthYear)));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<Expense>>> anomalies(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getAnomalies(user.getId())));
    }

    @GetMapping("/predict")
    public ResponseEntity<ApiResponse<Map<String, Object>>> predict(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.predict(user.getId())));
    }

    @PostMapping(value = "/receipts/scan-and-save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Expense>> scanReceipt(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.scanReceipt(user.getId(), file), "Receipt scanned"));
    }
}
