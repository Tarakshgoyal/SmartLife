package com.smartlife.health.repository;

import com.smartlife.health.model.HealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthLogRepository extends JpaRepository<HealthLog, UUID> {

    Page<HealthLog> findByUserIdOrderByLogDateDesc(UUID userId, Pageable pageable);

    @Query("SELECT h FROM HealthLog h WHERE h.user.id = :userId " +
           "AND h.logDate BETWEEN :from AND :to ORDER BY h.logDate ASC")
    List<HealthLog> findByUserAndDateRange(@Param("userId") UUID userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    Optional<HealthLog> findByUserIdAndLogDate(UUID userId, LocalDate logDate);

    @Query("SELECT AVG(h.sleepHours) FROM HealthLog h WHERE h.user.id = :userId " +
           "AND h.logDate >= :since AND h.sleepHours IS NOT NULL")
    Double getAvgSleepHours(@Param("userId") UUID userId, @Param("since") LocalDate since);

    @Query("SELECT AVG(h.moodScore) FROM HealthLog h WHERE h.user.id = :userId " +
           "AND h.logDate >= :since AND h.moodScore IS NOT NULL")
    Double getAvgMoodScore(@Param("userId") UUID userId, @Param("since") LocalDate since);

    @Query("SELECT AVG(h.weight) FROM HealthLog h WHERE h.user.id = :userId " +
           "AND h.logDate >= :since AND h.weight IS NOT NULL")
    Double getAvgWeight(@Param("userId") UUID userId, @Param("since") LocalDate since);

    Optional<HealthLog> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
