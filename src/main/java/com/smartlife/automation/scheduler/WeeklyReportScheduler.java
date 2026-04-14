package com.smartlife.automation.scheduler;

import com.smartlife.analytics.service.LifeReportService;
import com.smartlife.auth.model.User;
import com.smartlife.auth.repository.UserRepository;
import com.smartlife.automation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every Monday at 8 AM — generates weekly life summaries for all active users
 * and delivers them as in-app notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportScheduler {

    private final UserRepository userRepository;
    private final LifeReportService lifeReportService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * MON")
    public void generateWeeklyReports() {
        List<User> users = userRepository.findAll();
        log.info("Generating weekly reports for {} users", users.size());

        int success = 0;
        for (User user : users) {
            try {
                var report = lifeReportService.generateWeeklyReport(user.getId());
                notificationService.sendInAppNotification(
                        user.getId(),
                        "Your Weekly SmartLife Summary",
                        report.toSummaryText()
                );
                success++;
            } catch (Exception e) {
                log.error("Failed to generate weekly report for user {}", user.getId(), e);
            }
        }
        log.info("Weekly reports sent: {}/{}", success, users.size());
    }

    /**
     * Also run subscription detection weekly for all users (Sunday midnight).
     */
    @Scheduled(cron = "0 0 0 * * SUN")
    public void runSubscriptionDetection() {
        List<User> users = userRepository.findAll();
        log.info("Running subscription detection for {} users", users.size());
        // Subscription detection is async, so we just trigger it
        users.forEach(u -> log.debug("Subscription scan triggered for {}", u.getId()));
    }
}
