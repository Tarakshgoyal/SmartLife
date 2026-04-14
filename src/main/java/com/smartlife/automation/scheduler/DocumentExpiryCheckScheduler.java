package com.smartlife.automation.scheduler;

import com.smartlife.automation.service.ReminderService;
import com.smartlife.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentExpiryCheckScheduler {

    private final DocumentRepository documentRepository;
    private final ReminderService reminderService;

    /**
     * Runs daily at 8:00 AM — scans all documents expiring in the next 30 days
     * and creates reminders for users who don't have one yet.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiringDocuments() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(30);

        var expiring = documentRepository.findAllExpiringDocuments(from, to);
        log.info("Found {} documents expiring in the next 30 days", expiring.size());

        for (var doc : expiring) {
            if (doc.getExpiryDate() != null) {
                reminderService.scheduleDocumentExpiryReminder(
                        doc.getUser().getId(),
                        doc.getId(),
                        doc.getTitle() != null ? doc.getTitle() : doc.getFileName(),
                        doc.getExpiryDate()
                );
            }
        }
    }
}
