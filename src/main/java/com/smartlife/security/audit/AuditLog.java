package com.smartlife.security.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Layer 6: Audit Trail Entry.
 *
 * Records every significant data access and mutation event.
 * Immutable — audit records must never be modified or deleted (compliance requirement).
 *
 * Retained for 7 years (configurable) per financial/medical record compliance.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "actor_id"),
    @Index(name = "idx_audit_time", columnList = "event_time"),
    @Index(name = "idx_audit_event", columnList = "event_type"),
    @Index(name = "idx_audit_resource", columnList = "resource_type")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** User who performed the action (email or userId) */
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    /** What kind of event: DATA_ACCESS, DATA_MUTATION, AUTH_LOGIN, AUTH_LOGOUT,
     *  GDPR_EXPORT, GDPR_ERASE, CONSENT_GRANT, CONSENT_REVOKE, SECURITY_VIOLATION */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** The method/endpoint that was called */
    @Column(length = 255)
    private String action;

    /** Resource type being accessed: HEALTH, EXPENSE, DOCUMENT, USER */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /** Specific resource ID if applicable */
    @Column(name = "resource_id", length = 100)
    private String resourceId;

    /** GDPR processing purpose */
    @Column(length = 100)
    private String purpose;

    /** Comma-separated data categories accessed */
    @Column(length = 500)
    private String dataCategories;

    /** HTTP request details */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Outcome: SUCCESS, FAILURE, DENIED */
    @Column(length = 20)
    private String outcome;

    /** Additional details (e.g., failure reason) */
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "event_time", nullable = false, updatable = false)
    private LocalDateTime eventTime;

    @PrePersist
    protected void onCreate() {
        eventTime = LocalDateTime.now();
    }
}
