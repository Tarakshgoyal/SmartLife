package com.smartlife.security.gdpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as containing Personally Identifiable Information (PII).
 *
 * The GDPR AOP interceptor ({@link GDPRInterceptor}) automatically:
 *   - Masks this field in API responses (based on {@link MaskingStrategy})
 *   - Logs access to this field in the audit trail
 *   - Includes it in data export (right to portability)
 *   - Ensures it is deleted/anonymised on account deletion
 *
 * Usage:
 *   @Sensitive(strategy = MaskingStrategy.EMAIL)
 *   private String email;
 *
 *   @Sensitive(strategy = MaskingStrategy.LAST_FOUR)
 *   private String documentNumber;
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /** How to mask this field in non-admin API responses. */
    MaskingStrategy strategy() default MaskingStrategy.PARTIAL;

    /** Human-readable name for GDPR data export labelling. */
    String label() default "";

    /** Whether this field must be included in GDPR data exports. */
    boolean includeInExport() default true;

    enum MaskingStrategy {
        /** Show first char + *** + last char: "J***e" */
        PARTIAL,
        /** Show domain only: "***@gmail.com" */
        EMAIL,
        /** Show last 4 chars: "****4589" */
        LAST_FOUR,
        /** Replace entire value with "***" */
        FULL,
        /** Medical data — always fully masked unless explicitly requested */
        MEDICAL,
        /** Financial amounts — never masked (not PII) */
        NONE
    }
}
