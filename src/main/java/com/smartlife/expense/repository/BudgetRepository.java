package com.smartlife.expense.repository;

import com.smartlife.expense.model.Budget;
import com.smartlife.expense.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdAndMonthYear(UUID userId, String monthYear);
    Optional<Budget> findByUserIdAndCategoryAndMonthYear(UUID userId, ExpenseCategory category, String monthYear);
}
