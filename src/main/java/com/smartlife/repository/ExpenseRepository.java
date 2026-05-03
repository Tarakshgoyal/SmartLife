package com.smartlife.repository;

import com.smartlife.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdOrderByDateDesc(Long userId);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    List<Expense> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND FUNCTION('FORMATDATETIME', e.date, 'yyyy-MM') = :monthYear")
    List<Expense> findByUserIdAndMonthYear(@Param("userId") Long userId, @Param("monthYear") String monthYear);

    long countByUserId(Long userId);
}
