package com.smartlife.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts String fields
 * using AES-256-GCM with the application master key.
 *
 * Usage on entity fields:
 *   @Convert(converter = FieldEncryptor.class)
 *   private String sensitiveField;
 *
 * The converter is applied automatically on every read/write — the service
 * and repository layers see plain text and never deal with encryption directly.
 *
 * For per-user encryption (where different users need different keys),
 * use UserFieldEncryptor instead, which integrates with EncryptionKeyManager.
 */
@Converter
@Component
@Slf4j
public class FieldEncryptor implements AttributeConverter<String, String> {

    // Static reference — JPA instantiates converters outside Spring context
    private static EncryptionKeyManager keyManager;

    @Autowired
    public void setKeyManager(EncryptionKeyManager manager) {
        FieldEncryptor.keyManager = manager;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return attribute;
        if (keyManager == null) {
            log.warn("EncryptionKeyManager not initialised yet — storing plaintext temporarily");
            return attribute;
        }
        try {
            SecretKey key = keyManager.getMasterKey();
            return CryptoUtils.encrypt(attribute, key);
        } catch (Exception e) {
            log.error("Field encryption failed — REFUSING to store plaintext: {}", e.getMessage());
            throw new SecurityException("Cannot encrypt field — failing safe", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !CryptoUtils.isEncrypted(dbData)) return dbData;
        if (keyManager == null) {
            log.warn("EncryptionKeyManager not initialised — returning raw stored value");
            return dbData;
        }
        try {
            SecretKey key = keyManager.getMasterKey();
            return CryptoUtils.decrypt(dbData, key);
        } catch (Exception e) {
            log.error("Field decryption failed — returning masked value: {}", e.getMessage());
            return "[DECRYPTION_FAILED]";
        }
    }
}
