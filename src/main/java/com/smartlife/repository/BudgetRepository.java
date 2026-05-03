package com.smartlife.repository;

import com.smartlife.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserId(Long userId);

    List<Budget> findByUserIdAndMonthYear(Long userId, String monthYear);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);
}
