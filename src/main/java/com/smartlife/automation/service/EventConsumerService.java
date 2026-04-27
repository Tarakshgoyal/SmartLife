package com.smartlife.automation.service;

import com.smartlife.document.service.DocumentProcessingService.DocumentProcessedEvent;
import com.smartlife.expense.service.ExpenseService.ExpenseCreatedEvent;
import com.smartlife.health.service.HealthService.HealthAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventConsumerService {

    private final ReminderService reminderService;
    private final NotificationService notificationService;

    @KafkaListener(topics = "${smartlife.kafka.topics.document-processed}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onDocumentProcessed(DocumentProcessedEvent event) {
        log.debug("Document processed event received: {}", event.documentId());

        if (event.expiryDate() != null) {
            reminderService.scheduleDocumentExpiryReminder(
                    event.userId(), event.documentId(),
                    "Document " + event.documentId(),
                    event.expiryDate()
            );
        }
    }

    @KafkaListener(topics = "${smartlife.kafka.topics.expense-created}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onExpenseCreated(ExpenseCreatedEvent event) {
        log.debug("Expense created event received: {}", event.expenseId());

        if (event.isAnomaly()) {
            notificationService.sendInAppNotification(
                    event.userId(),
                    "Unusual Expense Detected",
                    String.format("An anomaly was detected in your %s spending of ₹%.2f",
                            event.category().name().toLowerCase().replace("_", " "),
                            event.amount().doubleValue())
            );
        }
    }

    @KafkaListener(topics = "${smartlife.kafka.topics.health-alert}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onHealthAlert(HealthAlertEvent event) {
        log.debug("Health alert event received for user: {}", event.userId());

        if (!event.warnings().isEmpty()) {
            String warningTitles = event.warnings().stream()
                    .map(w -> "• " + w.title())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            notificationService.sendInAppNotification(
                    event.userId(),
                    "Health Warning",
                    "We detected some health concerns:\n" + warningTitles
            );
        }
    }

    @KafkaListener(topics = "${smartlife.kafka.topics.reminder-triggered}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onReminderTriggered(ReminderService.ReminderTriggeredEvent event) {
        log.debug("Reminder triggered: {}", event.reminderId());
        notificationService.sendInAppNotification(event.userId(), event.title(), event.message());
    }
}
