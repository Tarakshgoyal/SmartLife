package com.smartlife.security.gdpr;

import com.smartlife.auth.model.User;
import com.smartlife.security.audit.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Layer 5: AOP-based GDPR Interceptor.
 *
 * Wraps every method annotated with @GDPRProtected and:
 *   1. Creates an audit log entry (WHO accessed WHAT and WHEN)
 *   2. Validates consent if @GDPRProtected(requireConsent=true)
 *   3. Applies field masking to the response via DataMaskingService
 *
 * Using AOP means privacy enforcement is a cross-cutting concern —
 * business logic stays clean and privacy rules are applied consistently.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class GDPRInterceptor {

    private final AuditLogger auditLogger;
    private final DataMaskingService maskingService;
    private final GDPRService gdprService;

    @Around("@annotation(gdpr)")
    public Object enforceGDPR(ProceedingJoinPoint joinPoint, GDPRProtected gdpr) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = auth != null ? auth.getName() : "anonymous";
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 1. Log data access in audit trail
        if (gdpr.auditAccess()) {
            auditLogger.logDataAccess(
                principal,
                joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName(),
                gdpr.purpose(),
                Arrays.asList(gdpr.dataCategories())
            );
        }

        // 2. Consent check
        if (gdpr.requireConsent() && auth != null && auth.getPrincipal() instanceof User user) {
            boolean hasConsent = gdprService.hasConsent(user.getId(), gdpr.purpose());
            if (!hasConsent) {
                log.warn("GDPR: Consent required but missing — userId={} purpose={}", user.getId(), gdpr.purpose());
                throw new SecurityException("Consent required for purpose: " + gdpr.purpose());
            }
        }

        // 3. Execute the actual business method
        Object result = joinPoint.proceed();

        // 4. Apply field masking to response (non-admins get masked PII)
        if (result != null && !isAdmin) {
            result = maskingService.mask(result, false);
        }

        return result;
    }
}
