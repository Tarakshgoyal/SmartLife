package com.smartlife.security.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Masks PII fields in API response objects based on @Sensitive annotations.
 *
 * The masking is applied at the controller level (by GDPRInterceptor) AFTER
 * business logic runs — service and repository layers always work with full data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataMaskingService {

    private final ObjectMapper objectMapper;

    /**
     * Apply masking to all @Sensitive fields in the given object.
     * Works recursively for nested objects and collections.
     *
     * @param obj     the response object to mask
     * @param isAdmin admins see full data; regular users get masked fields
     */
    public Object mask(Object obj, boolean isAdmin) {
        if (obj == null || isAdmin) return obj;
        try {
            maskFields(obj);
        } catch (Exception e) {
            log.warn("Field masking failed — returning unmasked response: {}", e.getMessage());
        }
        return obj;
    }

    private void maskFields(Object obj) throws IllegalAccessException {
        if (obj == null) return;

        Class<?> clazz = obj.getClass();

        // Handle collections
        if (obj instanceof Collection<?> col) {
            for (Object item : col) maskFields(item);
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) maskFields(value);
            return;
        }

        // Skip primitive types and JDK classes
        if (clazz.getName().startsWith("java.") || clazz.isPrimitive() || clazz.isEnum()) return;

        for (Field field : clazz.getDeclaredFields()) {
            Sensitive sensitive = field.getAnnotation(Sensitive.class);
            if (sensitive == null) continue;

            field.setAccessible(true);
            Object value = field.get(obj);
            if (value == null) continue;

            String masked = applyMask(value.toString(), sensitive.strategy());
            field.set(obj, masked);
        }
    }

    private String applyMask(String value, Sensitive.MaskingStrategy strategy) {
        if (value == null || value.isEmpty()) return value;
        return switch (strategy) {
            case FULL    -> "***";
            case MEDICAL -> "[MEDICAL_DATA_PROTECTED]";
            case NONE    -> value;
            case EMAIL   -> maskEmail(value);
            case LAST_FOUR -> maskLastFour(value);
            case PARTIAL -> maskPartial(value);
        };
    }

    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return "***";
        return "***" + email.substring(atIdx);
    }

    private String maskLastFour(String value) {
        String digits = value.replaceAll("[^a-zA-Z0-9]", "");
        if (digits.length() <= 4) return "****";
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private String maskPartial(String value) {
        if (value.length() <= 2) return "***";
        return value.charAt(0) + "*".repeat(Math.max(1, value.length() - 2)) + value.charAt(value.length() - 1);
    }
}
