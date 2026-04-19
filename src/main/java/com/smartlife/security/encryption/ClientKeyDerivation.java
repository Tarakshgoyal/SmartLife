package com.smartlife.security.encryption;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Layer 1: Client-Side Key Derivation (Zero-Knowledge Architecture).
 *
 * Derives a strong AES-256 encryption key from a user's master password
 * using PBKDF2-HMAC-SHA256 with 310,000 iterations (OWASP 2024 recommendation).
 *
 * The derived key NEVER leaves the client — the server only ever receives
 * the encrypted payload, making it impossible for even SmartLife's developers
 * to read user data without the master password.
 *
 * Flow:
 *   Master Password + Salt → PBKDF2 → DEK (Data Encryption Key)
 *   Sensitive Data + DEK   → AES-256-GCM → Encrypted Blob → Server
 *
 * This utility is used server-side to:
 *   1. Generate user-specific salts (returned to client on setup)
 *   2. Verify key derivation during password change flows
 *   3. Re-encrypt data when master password changes
 */
@Slf4j
@UtilityClass
public class ClientKeyDerivation {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int    PBKDF2_ITERATIONS = 310_000; // OWASP 2024 recommendation
    private static final int    SALT_BYTES        = 32;       // 256-bit salt
    private static final int    KEY_BITS          = 256;      // AES-256

    /**
     * Derive an AES-256 key from a master password and salt.
     *
     * @param masterPassword  user's master password (char[] to allow zeroing after use)
     * @param salt            per-user random salt (from {@link #generateSalt()})
     * @return derived AES-256 SecretKey
     */
    public static SecretKey deriveKey(char[] masterPassword, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            KeySpec spec = new PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_BITS);
            SecretKey tmp = factory.generateSecret(spec);
            // Zero out the PBEKeySpec to clear sensitive data from memory
            ((PBEKeySpec) spec).clearPassword();
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new SecurityException("Key derivation failed", e);
        }
    }

    /**
     * Derive key from String password (convenience method — password is cleared after use).
     */
    public static SecretKey deriveKey(String masterPassword, byte[] salt) {
        char[] chars = masterPassword.toCharArray();
        try {
            return deriveKey(chars, salt);
        } finally {
            java.util.Arrays.fill(chars, '\0'); // zero out in-memory password
        }
    }

    /** Generate a cryptographically strong random salt. */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /** Encode salt to Base64 for storage/transport. */
    public static String encodeSalt(byte[] salt) {
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Decode a Base64 salt string back to bytes. */
    public static byte[] decodeSalt(String base64Salt) {
        return Base64.getDecoder().decode(base64Salt);
    }

    /**
     * Verifies that a key derived from the given password+salt produces the same key
     * as a previously stored verification hash. Used to validate master password without
     * storing the password itself.
     */
    public static boolean verifyKey(String masterPassword, byte[] salt, String storedKeyHash) {
        try {
            SecretKey derived = deriveKey(masterPassword, salt);
            String derivedHash = Base64.getEncoder().encodeToString(derived.getEncoded());
            return derivedHash.equals(storedKeyHash);
        } catch (Exception e) {
            log.warn("Key verification failed: {}", e.getMessage());
            return false;
        }
    }
}
