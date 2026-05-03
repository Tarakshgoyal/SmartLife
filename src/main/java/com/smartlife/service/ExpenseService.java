package com.smartlife.service;

import com.smartlife.model.Budget;
import com.smartlife.model.Expense;
import com.smartlife.repository.BudgetRepository;
import com.smartlife.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    private static final DateTimeFormatter MY_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    public List<Expense> getExpenses(Long userId) {
        return expenseRepository.findByUserIdOrderByDateDesc(userId);
    }

    public Expense createExpense(Long userId, Expense req) {
        req.setUserId(userId);
        if (req.getDate() == null) req.setDate(LocalDate.now());
        return expenseRepository.save(req);
    }

    public void deleteExpense(Long id, Long userId) {
        Expense e = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        expenseRepository.delete(e);
    }

    public List<Budget> getBudgets(Long userId) {
        return budgetRepository.findByUserId(userId);
    }

    public List<Budget> getBudgetsByMonthYear(Long userId, String monthYear) {
        return budgetRepository.findByUserIdAndMonthYear(userId, monthYear);
    }

    public Budget createBudget(Long userId, Budget req) {
        req.setUserId(userId);
        return budgetRepository.save(req);
    }

    public Map<String, Object> getSummary(Long userId, String monthYear) {
        List<Expense> expenses = getExpensesForMonth(userId, monthYear);
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthYear(userId, monthYear);

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = budgets.stream()
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> breakdown = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        Map<String, Object> result = new HashMap<>();
        result.put("totalExpenses", totalExpenses);
        result.put("totalBudget", totalBudget);
        result.put("categoryBreakdown", breakdown);
        return result;
    }

    public Map<String, Object> getCurrentSummary(Long userId) {
        return getSummary(userId, YearMonth.now().format(MY_FMT));
    }

    public List<Expense> getAnomalies(Long userId) {
        List<Expense> all = expenseRepository.findByUserIdOrderByDateDesc(userId);
        // compute average per category
        Map<String, Double> avgByCategory = all.stream()
                .collect(Collectors.groupingBy(Expense::getCategory,
                        Collectors.averagingDouble(e -> e.getAmount().doubleValue())));

        return all.stream()
                .filter(e -> {
                    Double avg = avgByCategory.get(e.getCategory());
                    return avg != null && e.getAmount().doubleValue() > 2 * avg;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> predict(Long userId) {
        // average total of last 3 complete months
        LocalDate now = LocalDate.now();
        List<BigDecimal> monthTotals = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.from(now).minusMonths(i);
            List<Expense> expenses = getExpensesForMonth(userId, ym.format(MY_FMT));
            BigDecimal total = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            monthTotals.add(total);
        }
        BigDecimal sum = monthTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal predicted = monthTotals.isEmpty() ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(monthTotals.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();
        result.put("predictedTotal", predicted);
        result.put("confidence", 0.75);
        return result;
    }

    public Expense scanReceipt(Long userId, MultipartFile file) {
        Expense e = Expense.builder()
                .title("Scanned Receipt")
                .amount(BigDecimal.ZERO)
                .category("General")
                .date(LocalDate.now())
                .notes("Scanned from file: " + file.getOriginalFilename())
                .userId(userId)
                .build();
        return expenseRepository.save(e);
    }

    private List<Expense> getExpensesForMonth(Long userId, String monthYear) {
        // parse monthYear e.g. "2025-01"
        YearMonth ym = YearMonth.parse(monthYear, MY_FMT);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        return expenseRepository.findByUserIdAndDateBetween(userId, from, to);
    }
}
