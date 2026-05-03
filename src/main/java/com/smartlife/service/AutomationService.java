package com.smartlife.service;

import com.smartlife.model.Notification;
import com.smartlife.model.Reminder;
import com.smartlife.repository.NotificationRepository;
import com.smartlife.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutomationService {

    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;

    public List<Reminder> getReminders(Long userId) {
        return reminderRepository.findByUserIdOrderByDueDateAsc(userId);
    }

    public Reminder createReminder(Long userId, Reminder req) {
        req.setUserId(userId);
        return reminderRepository.save(req);
    }

    public Reminder updateReminder(Long id, Long userId, Reminder req) {
        Reminder existing = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        if (req.getTitle() != null) existing.setTitle(req.getTitle());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getDueDate() != null) existing.setDueDate(req.getDueDate());
        if (req.getFrequency() != null) existing.setFrequency(req.getFrequency());
        if (req.getCategory() != null) existing.setCategory(req.getCategory());
        existing.setCompleted(req.isCompleted());
        return reminderRepository.save(existing);
    }

    public void deleteReminder(Long id, Long userId) {
        Reminder r = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        reminderRepository.delete(r);
    }

    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Notification createOrMarkReadNotification(Long userId, Notification req) {
        if (req.getId() != null) {
            // mark read
            Notification existing = notificationRepository.findByIdAndUserId(req.getId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
            existing.setRead(true); // Lombok generates setRead() for field 'read'
            return notificationRepository.save(existing);
        }
        req.setUserId(userId);
        req.setCreatedAt(LocalDateTime.now());
        req.setRead(false);
        return notificationRepository.save(req);
    }
}
