package com.smartlife.automation.scheduler;

import com.smartlife.automation.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderService reminderService;

    /**
     * Fires every 5 minutes — checks for due reminders and dispatches them via Kafka.
     */
    @Scheduled(fixedDelay = 300_000)
    public void processDueReminders() {
        log.debug("Processing due reminders...");
        var sent = reminderService.processAndSendDueReminders();
        if (!sent.isEmpty()) {
            log.info("Dispatched {} reminder(s)", sent.size());
        }
    }
}
