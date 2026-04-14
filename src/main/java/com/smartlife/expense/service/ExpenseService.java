package com.smartlife.expense.service;

import com.smartlife.auth.model.User;
import com.smartlife.common.exception.SmartLifeException;
import com.smartlife.expense.dto.*;
import com.smartlife.expense.model.Budget;
import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.repository.BudgetRepository;
import com.smartlife.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final ExpenseCategoryClassifier categoryClassifier;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final DateTimeFormatter MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional
    public ExpenseDto createExpense(ExpenseCreateRequest request, User user) {
        // Auto-classify if category not provided
        ExpenseCategory category = request.category() != null
                ? request.category()
                : categoryClassifier.classify(request.description(), request.merchant());

        Expense expense = Expense.builder()
                .user(user)
                .amount(request.amount())
                .description(request.description())
                .merchant(request.merchant())
                .category(category)
                .expenseDate(request.expenseDate() != null ? request.expenseDate() : LocalDate.now())
                .isRecurring(Boolean.TRUE.equals(request.isRecurring()))
                .recurringInterval(request.recurringInterval())
                .source("MANUAL")
                .build();

        // Check for anomaly
        AnomalyDetectionService.AnomalyResult anomaly = anomalyDetectionService.checkAnomaly(expense);
        expense.setAnomaly(anomaly.isAnomaly());
        expense.setAnomalyReason(anomaly.reason());

        expenseRepository.save(expense);
        log.info("Expense created: {} for user {}", expense.getId(), user.getEmail());

        try {
            kafkaTemplate.send("smartlife.expense.created", expense.getId().toString(),
                    new ExpenseCreatedEvent(expense.getId(), user.getId(), expense.getAmount(),
                            expense.getCategory(), anomaly.isAnomaly()));
        } catch (Exception e) {
            log.warn("Kafka unavailable — expense event not published: {}", e.getMessage());
        }

        return ExpenseDto.from(expense);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDto> getUserExpenses(UUID userId, ExpenseCategory category, Pageable pageable) {
        Page<Expense> page = category != null
                ? expenseRepository.findByUserIdAndCategoryOrderByExpenseDateDesc(userId, category, pageable)
                : expenseRepository.findByUserIdOrderByExpenseDateDesc(userId, pageable);
        return page.map(ExpenseDto::from);
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryDto getMonthlySummary(UUID userId, String monthYear) {
        YearMonth ym = YearMonth.parse(monthYear);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal total = expenseRepository.getTotalByUserAndDateRange(userId, from, to);
        List<ExpenseRepository.CategoryTotal> categoryTotals =
                expenseRepository.getCategoryTotals(userId, from, to);

        Map<String, BigDecimal> breakdown = categoryTotals.stream()
                .collect(Collectors.toMap(
                        ct -> ct.getCategory().name(),
                        ExpenseRepository.CategoryTotal::getTotal
                ));

        List<BudgetStatusDto> budgetStatuses = getBudgetStatuses(userId, monthYear, breakdown);

        return new ExpenseSummaryDto(monthYear, total != null ? total : BigDecimal.ZERO,
                breakdown, budgetStatuses);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> getAnomalies(UUID userId) {
        return expenseRepository.findAnomalies(userId).stream().map(ExpenseDto::from).toList();
    }

    @Transactional
    public BudgetDto setBudget(BudgetCreateRequest request, User user) {
        String monthYear = request.monthYear() != null
                ? request.monthYear()
                : YearMonth.now().format(MONTH_YEAR_FMT);

        Budget budget = budgetRepository
                .findByUserIdAndCategoryAndMonthYear(user.getId(), request.category(), monthYear)
                .orElseGet(() -> Budget.builder().user(user).category(request.category()).build());

        budget.setLimitAmount(request.limitAmount());
        budget.setMonthYear(monthYear);
        budgetRepository.save(budget);

        return BudgetDto.from(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetDto> getBudgets(UUID userId, String monthYear) {
        return budgetRepository.findByUserIdAndMonthYear(userId, monthYear)
                .stream().map(BudgetDto::from).toList();
    }

    @Transactional
    public void deleteExpense(UUID expenseId, UUID userId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new SmartLifeException("Expense not found", HttpStatus.NOT_FOUND));
        expenseRepository.delete(expense);
    }

    private List<BudgetStatusDto> getBudgetStatuses(UUID userId, String monthYear,
                                                      Map<String, BigDecimal> spending) {
        return budgetRepository.findByUserIdAndMonthYear(userId, monthYear).stream()
                .map(b -> {
                    BigDecimal spent = spending.getOrDefault(b.getCategory().name(), BigDecimal.ZERO);
                    double pct = b.getLimitAmount().doubleValue() > 0
                            ? (spent.doubleValue() / b.getLimitAmount().doubleValue()) * 100 : 0;
                    return new BudgetStatusDto(b.getCategory(), b.getLimitAmount(), spent, pct);
                })
                .toList();
    }

    public record ExpenseCreatedEvent(UUID expenseId, UUID userId, BigDecimal amount,
                                       ExpenseCategory category, boolean isAnomaly) {}
}
