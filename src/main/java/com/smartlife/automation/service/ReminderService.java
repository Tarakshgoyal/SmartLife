package com.smartlife.automation.service;

import com.smartlife.auth.model.User;
import com.smartlife.auth.repository.UserRepository;
import com.smartlife.automation.dto.ReminderCreateRequest;
import com.smartlife.automation.dto.ReminderDto;
import com.smartlife.automation.model.Reminder;
import com.smartlife.automation.model.ReminderType;
import com.smartlife.automation.repository.ReminderRepository;
import com.smartlife.common.exception.SmartLifeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public ReminderDto createReminder(ReminderCreateRequest request, User user) {
        Reminder reminder = Reminder.builder()
                .user(user)
                .title(request.title())
                .message(request.message())
                .type(request.type())
                .relatedEntityId(request.relatedEntityId())
                .scheduledAt(request.scheduledAt())
                .recurring(Boolean.TRUE.equals(request.recurring()))
                .cronExpression(request.cronExpression())
                .build();

        reminderRepository.save(reminder);
        log.info("Reminder created: {} for user {}", reminder.getId(), user.getEmail());
        return ReminderDto.from(reminder);
    }

    @Transactional
    public void scheduleDocumentExpiryReminder(UUID userId, UUID documentId,
                                                String docTitle, LocalDate expiryDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new SmartLifeException("User not found", HttpStatus.NOT_FOUND));

        // Create reminders at 30 days and 7 days before expiry
        int[] daysBefore = {30, 7};
        for (int days : daysBefore) {
            LocalDateTime reminderTime = expiryDate.minusDays(days).atTime(9, 0);
            if (reminderTime.isAfter(LocalDateTime.now())) {
                Reminder reminder = Reminder.builder()
                        .user(user)
                        .title("Document Expiring Soon: " + docTitle)
                        .message(String.format("'%s' will expire on %s (%d days remaining). " +
                                "Please renew it soon.", docTitle, expiryDate, days))
                        .type(ReminderType.DOCUMENT_EXPIRY)
                        .relatedEntityId(documentId)
                        .scheduledAt(reminderTime)
                        .build();
                reminderRepository.save(reminder);
            }
        }
        log.info("Document expiry reminders scheduled for document {} (expiry: {})", documentId, expiryDate);
    }

    @Transactional
    public List<ReminderDto> processAndSendDueReminders() {
        List<Reminder> due = reminderRepository.findDueReminders(LocalDateTime.now());

        for (Reminder reminder : due) {
            try {
                kafkaTemplate.send("smartlife.reminder.triggered",
                        reminder.getUser().getId().toString(),
                        new ReminderTriggeredEvent(
                                reminder.getId(), reminder.getUser().getId(),
                                reminder.getTitle(), reminder.getMessage(), reminder.getType()
                        ));

                reminder.setSent(true);
                reminder.setSentAt(LocalDateTime.now());
                reminderRepository.save(reminder);
                log.debug("Reminder sent: {}", reminder.getId());
            } catch (Exception e) {
                log.error("Failed to send reminder {}", reminder.getId(), e);
            }
        }

        return due.stream().map(ReminderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReminderDto> getUserReminders(UUID userId) {
        return reminderRepository.findByUserIdAndSentFalseOrderByScheduledAtAsc(userId)
                .stream().map(ReminderDto::from).toList();
    }

    @Transactional
    public void deleteReminder(UUID reminderId, UUID userId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> new SmartLifeException("Reminder not found", HttpStatus.NOT_FOUND));
        reminderRepository.delete(reminder);
    }

    public record ReminderTriggeredEvent(UUID reminderId, UUID userId,
                                          String title, String message, ReminderType type) {}
}
