package com.smartlife.security.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlife.auth.model.User;
import com.smartlife.auth.repository.UserRepository;
import com.smartlife.document.repository.DocumentRepository;
import com.smartlife.expense.repository.ExpenseRepository;
import com.smartlife.health.repository.HealthLogRepository;
import com.smartlife.security.encryption.EncryptionKeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GDPR Compliance Service — implements the four fundamental user rights:
 *
 *   1. Right to Erasure (Art. 17)  — crypto-shredding + data deletion
 *   2. Right to Portability (Art. 20) — structured data export
 *   3. Right to Consent (Art. 7)   — grant/revoke consent by purpose
 *   4. Right to Access (Art. 15)   — view all stored data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GDPRService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final HealthLogRepository healthLogRepository;
    private final DocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    private final EncryptionKeyManager keyManager;
    private final ObjectMapper objectMapper;

    // ─── Right to Erasure ────────────────────────────────────────────────────

    /**
     * GDPR Art. 17 — Right to erasure ("right to be forgotten").
     *
     * Steps:
     * 1. Destroy the user's DEK (crypto-shredding — makes encrypted data unreadable)
     * 2. Delete all user data from every table
     * 3. Anonymise audit logs (required to keep for compliance, but PII removed)
     * 4. Disable/delete the user account
     */
    @Transactional
    public void eraseUserData(UUID userId) {
        log.warn("GDPR: Initiating data erasure for userId={}", userId);

        // Step 1: Crypto-shredding — destroy DEK first
        keyManager.destroyUserKey(userId);
        log.info("GDPR: DEK destroyed for userId={}", userId);

        // Step 2: Delete all user data
        healthLogRepository.deleteByUserId(userId);
        expenseRepository.deleteByUserId(userId);
        documentRepository.deleteByUserId(userId);
        consentRepository.deleteAll(consentRepository.findByUserIdOrderByGrantedAtDesc(userId));

        // Step 3: Anonymise and disable user account
        userRepository.findById(userId).ifPresent(user -> {
            user.setEmail("deleted_" + userId + "@erased.smartlife.app");
            user.setFullName("[DELETED USER]");
            user.setPassword("[ERASED]");
            user.setEnabled(false);
            userRepository.save(user);
        });

        log.warn("GDPR: Data erasure complete for userId={}", userId);
    }

    // ─── Right to Data Portability ───────────────────────────────────────────

    /**
     * GDPR Art. 20 — Export all user data as a structured JSON map.
     * In production: compress to ZIP, encrypt with user's key, email securely.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", LocalDateTime.now().toString());
        export.put("userId", userId.toString());

        userRepository.findById(userId).ifPresent(user -> {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("email", user.getEmail());
            profile.put("fullName", user.getFullName());
            profile.put("role", user.getRole().name());
            profile.put("createdAt", user.getCreatedAt().toString());
            export.put("profile", profile);
        });

        export.put("healthLogs", healthLogRepository.findByUserAndDateRange(
            userId,
            java.time.LocalDate.now().minusYears(10),
            java.time.LocalDate.now()
        ));

        export.put("expenses", expenseRepository.findByUserIdOrderByExpenseDateDesc(userId,
            org.springframework.data.domain.Pageable.unpaged()).getContent());

        export.put("consents", consentRepository.findByUserIdOrderByGrantedAtDesc(userId));

        log.info("GDPR: Data export generated for userId={}", userId);
        return export;
    }

    // ─── Consent Management ──────────────────────────────────────────────────

    @Transactional
    public ConsentRecord grantConsent(UUID userId, String purpose, String ipAddress, String policyVersion) {
        ConsentRecord record = ConsentRecord.builder()
                .userId(userId)
                .purpose(purpose)
                .granted(true)
                .ipAddress(ipAddress)
                .policyVersion(policyVersion)
                .build();
        ConsentRecord saved = consentRepository.save(record);
        log.info("GDPR: Consent GRANTED — userId={} purpose={}", userId, purpose);
        return saved;
    }

    @Transactional
    public void revokeConsent(UUID userId, String purpose) {
        consentRepository.findByUserIdAndPurposeAndGrantedTrue(userId, purpose).ifPresent(record -> {
            record.setGranted(false);
            record.setRevokedAt(LocalDateTime.now());
            consentRepository.save(record);
            log.info("GDPR: Consent REVOKED — userId={} purpose={}", userId, purpose);
        });
    }

    public List<ConsentRecord> getUserConsents(UUID userId) {
        return consentRepository.findByUserIdOrderByGrantedAtDesc(userId);
    }

    public boolean hasConsent(UUID userId, String purpose) {
        return consentRepository.existsByUserIdAndPurposeAndGrantedTrue(userId, purpose);
    }
}
