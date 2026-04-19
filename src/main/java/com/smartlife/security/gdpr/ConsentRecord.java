package com.smartlife.security.gdpr;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a user's consent to a specific data processing purpose.
 * Required by GDPR Article 7 — consent must be documented and revocable.
 */
@Entity
@Table(name = "consent_records", indexes = {
    @Index(name = "idx_consent_user", columnList = "user_id"),
    @Index(name = "idx_consent_purpose", columnList = "purpose")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsentRecord {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The purpose for which consent is granted, e.g. "ai_health_analysis" */
    @Column(nullable = false, length = 100)
    private String purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    /** IP address at time of consent (GDPR audit requirement) */
    @Column(length = 45)
    private String ipAddress;

    /** Version of the privacy policy user agreed to */
    @Column(length = 20)
    private String policyVersion;

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
    }
}
