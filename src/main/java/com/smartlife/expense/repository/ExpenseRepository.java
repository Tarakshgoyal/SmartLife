package com.smartlife.expense.repository;

import com.smartlife.expense.model.Expense;
import com.smartlife.expense.model.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByUserIdOrderByExpenseDateDesc(UUID userId, Pageable pageable);

    Page<Expense> findByUserIdAndCategoryOrderByExpenseDateDesc(UUID userId, ExpenseCategory category, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId " +
           "AND e.expenseDate BETWEEN :from AND :to ORDER BY e.expenseDate DESC")
    List<Expense> findByUserAndDateRange(@Param("userId") UUID userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("SELECT e.category AS category, SUM(e.amount) AS total " +
           "FROM Expense e WHERE e.user.id = :userId " +
           "AND e.expenseDate BETWEEN :from AND :to " +
           "GROUP BY e.category ORDER BY total DESC")
    List<CategoryTotal> getCategoryTotals(@Param("userId") UUID userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId " +
           "AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal getTotalByUserAndDateRange(@Param("userId") UUID userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.isAnomaly = true " +
           "ORDER BY e.expenseDate DESC")
    List<Expense> findAnomalies(@Param("userId") UUID userId);

    @Query("SELECT AVG(e.amount) FROM Expense e WHERE e.user.id = :userId " +
           "AND e.category = :category AND e.expenseDate >= :since")
    BigDecimal getAverageByCategory(@Param("userId") UUID userId,
                                     @Param("category") ExpenseCategory category,
                                     @Param("since") LocalDate since);

    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    // Projection for category totals
    interface CategoryTotal {
        ExpenseCategory getCategory();
        BigDecimal getTotal();
    }
}
