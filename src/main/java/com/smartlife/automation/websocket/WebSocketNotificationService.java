package com.smartlife.automation.websocket;

import com.smartlife.automation.service.NotificationService.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pushes real-time notifications to connected WebSocket clients.
 *
 * Clients subscribe to: /user/{userId}/queue/notifications
 *
 * WebSocket flow:
 *   Event → Kafka → EventConsumerService → NotificationService (Redis store)
 *                                        → WebSocketNotificationService (real-time push)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Push notification to a specific user's WebSocket session. */
    public void pushToUser(UUID userId, Notification notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    notification
            );
            log.debug("WebSocket notification pushed to user {}: {}", userId, notification.title());
        } catch (Exception e) {
            log.warn("Failed to push WebSocket notification to user {}: {}", userId, e.getMessage());
            // Non-fatal — notification is already stored in Redis as fallback
        }
    }

    /** Broadcast a system-wide alert to all connected users (admin use). */
    public void broadcastSystemAlert(String title, String message) {
        Notification notification = new Notification(
                UUID.randomUUID().toString(), title, message,
                java.time.LocalDateTime.now().toString(), false);
        messagingTemplate.convertAndSend("/topic/system-alerts", notification);
        log.info("System alert broadcast: {}", title);
    }
}
