package com.smartlife.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * Service for recording audit log entries.
 * All writes are async so they never block the main request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void logDataAccess(String actorId, String action, String purpose, List<String> dataCategories) {
        save(AuditLog.builder()
                .actorId(actorId)
                .eventType("DATA_ACCESS")
                .action(action)
                .purpose(purpose)
                .dataCategories(String.join(",", dataCategories))
                .outcome("SUCCESS")
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .build());
    }

    @Async
    public void logDataMutation(String actorId, String action, String resourceType, String resourceId) {
        save(AuditLog.builder()
                .actorId(actorId)
                .eventType("DATA_MUTATION")
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .outcome("SUCCESS")
                .ipAddress(getClientIp())
                .build());
    }

    @Async
    public void logAuthEvent(String actorId, String eventType, String outcome, String details) {
        save(AuditLog.builder()
                .actorId(actorId)
                .eventType(eventType)
                .outcome(outcome)
                .details(details)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .build());
    }

    @Async
    public void logGdprEvent(String actorId, String eventType, String details) {
        save(AuditLog.builder()
                .actorId(actorId)
                .eventType(eventType)
                .purpose("gdpr_compliance")
                .outcome("SUCCESS")
                .details(details)
                .ipAddress(getClientIp())
                .build());
    }

    @Async
    public void logSecurityViolation(String actorId, String action, String details) {
        log.error("SECURITY VIOLATION: actor={} action={} details={}", actorId, action, details);
        save(AuditLog.builder()
                .actorId(actorId != null ? actorId : "unknown")
                .eventType("SECURITY_VIOLATION")
                .action(action)
                .outcome("DENIED")
                .details(details)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .build());
    }

    private void save(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never crash the main application
            log.error("AUDIT: Failed to write audit log entry: {}", e.getMessage());
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            String forwarded = attrs.getRequest().getHeader("X-Forwarded-For");
            return forwarded != null ? forwarded.split(",")[0].trim()
                                    : attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            String ua = attrs.getRequest().getHeader("User-Agent");
            return ua != null ? ua.substring(0, Math.min(ua.length(), 500)) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
