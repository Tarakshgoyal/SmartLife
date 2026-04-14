package com.smartlife.expense.repository;

import com.smartlife.expense.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserIdAndActiveTrue(UUID userId);

    Optional<Subscription> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
           "AND s.nextExpectedDate BETWEEN :from AND :to AND s.active = true")
    List<Subscription> findUpcomingRenewals(@Param("userId") UUID userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query("SELECT s FROM Subscription s WHERE s.user.id = :userId " +
           "AND s.markedUnused = true AND s.active = true")
    List<Subscription> findUnusedSubscriptions(@Param("userId") UUID userId);
}
