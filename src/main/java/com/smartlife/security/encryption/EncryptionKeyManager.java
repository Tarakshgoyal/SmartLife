package com.smartlife.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a two-tier key hierarchy:
 *
 *   KEK (Key Encryption Key) — one master key per application instance,
 *       loaded from environment variable / Vault. Encrypts DEKs at rest.
 *
 *   DEK (Data Encryption Key) — one per user, generated on first use,
 *       stored encrypted in the database (encrypted by the KEK).
 *
 * Crypto-Shredding: destroying a user's DEK permanently makes all their
 * encrypted data unreadable — the perfect GDPR "right to be forgotten" implementation.
 *
 * In production: replace the in-memory DEK store with a UserKeyRepository
 * that persists KEK-encrypted DEKs in the database.
 */
@Service
@Slf4j
public class EncryptionKeyManager {

    /** Master key (KEK) — loaded from env var SMARTLIFE_MASTER_KEY or config. */
    private final SecretKey masterKey;

    /** In-memory DEK cache: userId -> plaintext DEK (never persisted as-is). */
    private final ConcurrentHashMap<UUID, SecretKey> dekCache = new ConcurrentHashMap<>();

    /**
     * Encrypted DEK store: userId -> KEK-encrypted DEK (this would be persisted in DB in production).
     * Production: replace with @Repository that reads/writes encrypted DEKs from a user_keys table.
     */
    private final ConcurrentHashMap<UUID, String> encryptedDekStore = new ConcurrentHashMap<>();

    public EncryptionKeyManager(
            @Value("${smartlife.encryption.master-key:}") String configuredMasterKey) {
        this.masterKey = loadOrGenerateMasterKey(configuredMasterKey);
        log.info("EncryptionKeyManager initialised — master key loaded");
    }

    /**
     * Get (or create) the Data Encryption Key for a specific user.
     * The first call per user generates a new DEK and stores it encrypted.
     */
    public SecretKey getUserKey(UUID userId) {
        return dekCache.computeIfAbsent(userId, uid -> {
            String encryptedDek = encryptedDekStore.get(uid);
            if (encryptedDek != null) {
                // Decrypt the stored DEK using the master KEK
                String decodedDek = CryptoUtils.decrypt(encryptedDek, masterKey);
                return CryptoUtils.decodeKey(decodedDek);
            }
            // First access — generate a brand new DEK for this user
            return createAndStoreUserKey(uid);
        });
    }

    /**
     * Crypto-shredding: destroy a user's DEK.
     * After this call, ALL data encrypted with that DEK becomes permanently unreadable —
     * even if encrypted blobs remain in the database.
     *
     * This is the gold-standard implementation of GDPR "right to erasure".
     */
    public void destroyUserKey(UUID userId) {
        dekCache.remove(userId);
        encryptedDekStore.remove(userId);
        log.warn("SECURITY: DEK destroyed for userId={} — all encrypted data is now permanently unreadable", userId);
    }

    /**
     * Returns the application-level master key (used for non-user-specific encryption,
     * e.g., encrypting fields that belong to the system rather than a specific user).
     */
    public SecretKey getMasterKey() {
        return masterKey;
    }

    /** Check whether a user's encryption key exists (i.e., account is not shredded). */
    public boolean hasUserKey(UUID userId) {
        return encryptedDekStore.containsKey(userId) || dekCache.containsKey(userId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private SecretKey createAndStoreUserKey(UUID userId) {
        SecretKey dek = CryptoUtils.generateAesKey();
        // Encrypt the DEK with the master KEK before storing
        String encryptedDek = CryptoUtils.encrypt(CryptoUtils.encodeKey(dek), masterKey);
        encryptedDekStore.put(userId, encryptedDek);
        log.debug("New DEK created for userId={}", userId);
        return dek;
    }

    private SecretKey loadOrGenerateMasterKey(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(configured);
                if (keyBytes.length == 32) { // 256-bit
                    return new SecretKeySpec(keyBytes, "AES");
                }
            } catch (Exception ignored) {}
        }
        // Generate an ephemeral master key — WARN: keys won't survive restarts!
        // Set SMARTLIFE_MASTER_KEY env var or smartlife.encryption.master-key in config for persistence.
        log.warn("SECURITY WARNING: No master key configured — using ephemeral key. " +
                 "Set smartlife.encryption.master-key to a stable 32-byte Base64 value for production!");
        SecretKey ephemeral = CryptoUtils.generateAesKey();
        log.info("Generated ephemeral master key: {}", CryptoUtils.encodeKey(ephemeral));
        return ephemeral;
    }
}
