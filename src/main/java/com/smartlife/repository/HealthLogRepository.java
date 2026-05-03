package com.smartlife.repository;

import com.smartlife.model.HealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    Page<HealthLog> findByUserId(Long userId, Pageable pageable);

    List<HealthLog> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    Optional<HealthLog> findByIdAndUserId(Long id, Long userId);

    List<HealthLog> findByUserIdAndDateAfter(Long userId, LocalDate from);
}
