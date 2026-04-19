package com.smartlife.security.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByActorIdOrderByEventTimeDesc(String actorId, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByEventTimeDesc(String eventType, Pageable pageable);

    List<AuditLog> findByActorIdAndEventTimeBetween(String actorId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a FROM AuditLog a WHERE a.eventTime > :since AND a.eventType = 'SECURITY_VIOLATION'")
    List<AuditLog> findRecentSecurityViolations(LocalDateTime since);

    long countByActorIdAndEventTypeAndEventTimeAfter(String actorId, String eventType, LocalDateTime since);
}
