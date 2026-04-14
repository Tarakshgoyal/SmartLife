package com.smartlife.expense;

import com.smartlife.auth.model.User;
import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.expense.service.AnomalyDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock private ExpenseRepository expenseRepository;

    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService(expenseRepository);
    }

    @Test
    void detectsAnomalyWhenAmountExceedsThreshold() {
        User user = buildUser();
        Expense expense = buildExpense(user, new BigDecimal("5000.00"), ExpenseCategory.DINING);

        when(expenseRepository.getAverageByCategory(any(), eq(ExpenseCategory.DINING), any()))
                .thenReturn(new BigDecimal("500.00"));

        var result = service.checkAnomaly(expense);

        assertThat(result.isAnomaly()).isTrue();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void noAnomalyForNormalAmount() {
        User user = buildUser();
        Expense expense = buildExpense(user, new BigDecimal("600.00"), ExpenseCategory.DINING);

        when(expenseRepository.getAverageByCategory(any(), eq(ExpenseCategory.DINING), any()))
                .thenReturn(new BigDecimal("500.00"));

        var result = service.checkAnomaly(expense);
        assertThat(result.isAnomaly()).isFalse();
    }

    @Test
    void noAnomalyWhenNoHistory() {
        User user = buildUser();
        Expense expense = buildExpense(user, new BigDecimal("999.00"), ExpenseCategory.DINING);

        when(expenseRepository.getAverageByCategory(any(), any(), any()))
                .thenReturn(null);

        var result = service.checkAnomaly(expense);
        assertThat(result.isAnomaly()).isFalse();
    }

    @Test
    void smallAmountSkipsAnomalyCheck() {
        User user = buildUser();
        Expense expense = buildExpense(user, new BigDecimal("50.00"), ExpenseCategory.DINING);

        var result = service.checkAnomaly(expense);
        assertThat(result.isAnomaly()).isFalse();
    }

    private User buildUser() {
        User user = new User();
        user = User.builder().id(UUID.randomUUID()).email("test@test.com")
                .fullName("Test").password("pw").build();
        return user;
    }

    private Expense buildExpense(User user, BigDecimal amount, ExpenseCategory category) {
        return Expense.builder()
                .user(user)
                .amount(amount)
                .category(category)
                .expenseDate(LocalDate.now())
                .description("test expense")
                .build();
    }
}
