package com.smartlife.automation.service;

import com.smartlife.automation.websocket.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Stores in-app notifications in Redis (list per user) and pushes via WebSocket.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    @Lazy private final WebSocketNotificationService webSocketNotificationService;

    private static final String NOTIFICATIONS_KEY_PREFIX = "notifications:";
    private static final int MAX_NOTIFICATIONS = 50;
    private static final long TTL_DAYS = 7;

    public void sendInAppNotification(UUID userId, String title, String message) {
        String key = NOTIFICATIONS_KEY_PREFIX + userId;

        Notification notification = new Notification(
                UUID.randomUUID().toString(), title, message,
                LocalDateTime.now().toString(), false
        );

        // Persist to Redis for polling-based retrieval
        try {
            redisTemplate.opsForList().leftPush(key, notification);
            redisTemplate.opsForList().trim(key, 0, MAX_NOTIFICATIONS - 1);
            redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis unavailable — notification not persisted: {}", e.getMessage());
        }

        // Push real-time via WebSocket (best-effort)
        webSocketNotificationService.pushToUser(userId, notification);

        log.info("In-app notification stored for user {}: {}", userId, title);
    }

    @SuppressWarnings("unchecked")
    public List<Notification> getNotifications(UUID userId) {
        String key = NOTIFICATIONS_KEY_PREFIX + userId;
        List<Object> raw;
        try {
            raw = redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.warn("Redis unavailable — returning empty notifications: {}", e.getMessage());
            return List.of();
        }
        if (raw == null) return List.of();

        List<Notification> notifications = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Notification n) {
                notifications.add(n);
            }
        }
        return notifications;
    }

    public void clearNotifications(UUID userId) {
        try {
            redisTemplate.delete(NOTIFICATIONS_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis unavailable — notifications not cleared: {}", e.getMessage());
        }
    }

    public record Notification(
            String id,
            String title,
            String message,
            String timestamp,
            boolean read
    ) {}
}
