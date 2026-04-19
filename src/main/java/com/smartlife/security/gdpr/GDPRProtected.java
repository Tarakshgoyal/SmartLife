package com.smartlife.security.gdpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller/Service method annotation that activates the GDPR interceptor.
 *
 * When applied to a controller method, the {@link GDPRInterceptor} AOP aspect:
 *   1. Logs the data access in the audit trail (WHO accessed WHAT and WHEN)
 *   2. Applies field masking to the response based on @Sensitive annotations
 *   3. Validates that the user has consented to the data processing purpose
 *
 * Usage:
 *   @GDPRProtected(purpose = "health_dashboard_view", auditAccess = true)
 *   @GetMapping("/health/insights")
 *   public ResponseEntity<...> getInsights(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GDPRProtected {

    /** The data processing purpose — must match a consent record. */
    String purpose() default "general";

    /** Whether to create an audit log entry for every invocation. */
    boolean auditAccess() default true;

    /** Data categories accessed (for audit log). */
    String[] dataCategories() default {};

    /** If true, require explicit consent before proceeding. */
    boolean requireConsent() default false;
}
