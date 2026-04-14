package com.smartlife.automation.repository;

import com.smartlife.automation.model.Reminder;
import com.smartlife.automation.model.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByUserIdAndSentFalseOrderByScheduledAtAsc(UUID userId);

    @Query("SELECT r FROM Reminder r WHERE r.sent = false AND r.scheduledAt <= :now")
    List<Reminder> findDueReminders(@Param("now") LocalDateTime now);

    List<Reminder> findByUserIdAndTypeAndSentFalse(UUID userId, ReminderType type);

    @Query("SELECT r FROM Reminder r WHERE r.relatedEntityId = :entityId AND r.sent = false")
    List<Reminder> findPendingByRelatedEntity(@Param("entityId") UUID entityId);
}
