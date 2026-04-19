package com.smartlife.security.encryption;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utilities.
 *
 * AES-GCM provides both confidentiality AND integrity — if encrypted data
 * is tampered with, decryption will fail with an exception (authenticated encryption).
 *
 * Format stored: Base64(IV[12 bytes] || Ciphertext || GCM-Tag[16 bytes])
 * Prefixed with "enc:" to distinguish encrypted from plaintext values.
 */
@Slf4j
@UtilityClass
public class CryptoUtils {

    public static final String ALGORITHM  = "AES/GCM/NoPadding";
    public static final String KEY_ALGO   = "AES";
    public static final int    KEY_BITS   = 256;
    public static final int    IV_BYTES   = 12;   // 96-bit IV — optimal for GCM
    public static final int    TAG_BITS   = 128;  // 128-bit authentication tag
    public static final String ENC_PREFIX = "enc:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Encrypt plaintext with the given AES key.
     * Returns Base64(IV || Ciphertext) prefixed with "enc:".
     */
    public static String encrypt(String plaintext, SecretKey key) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV so we can retrieve it on decryption
            byte[] combined = ByteBuffer.allocate(IV_BYTES + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new SecurityException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * Decrypt a value produced by {@link #encrypt}.
     * Returns null for null input; returns plaintext unchanged if not prefixed with "enc:".
     */
    public static String decrypt(String stored, SecretKey key) {
        if (stored == null) return null;
        if (!stored.startsWith(ENC_PREFIX)) return stored; // already plain (legacy row)
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv         = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("AES-256-GCM decryption failed — data may be tampered", e);
        }
    }

    /** Generate a fresh random AES-256 key. */
    public static SecretKey generateAesKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance(KEY_ALGO);
            gen.init(KEY_BITS, SECURE_RANDOM);
            return gen.generateKey();
        } catch (Exception e) {
            throw new SecurityException("AES key generation failed", e);
        }
    }

    /** Encode a SecretKey to a Base64 string for storage. */
    public static String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Decode a Base64 string back to a SecretKey. */
    public static SecretKey decodeKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, KEY_ALGO);
    }

    /** Check whether a stored value is encrypted. */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}
