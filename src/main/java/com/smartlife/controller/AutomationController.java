package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.Notification;
import com.smartlife.model.Reminder;
import com.smartlife.model.User;
import com.smartlife.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    @GetMapping("/reminders")
    public ResponseEntity<ApiResponse<List<Reminder>>> getReminders(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.getReminders(user.getId())));
    }

    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<Reminder>> createReminder(
            @AuthenticationPrincipal User user,
            @RequestBody Reminder req) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.createReminder(user.getId(), req), "Created"));
    }

    @PutMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Reminder>> updateReminder(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Reminder req) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.updateReminder(id, user.getId(), req), "Updated"));
    }

    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        automationService.deleteReminder(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(automationService.getNotifications(user.getId())));
    }

    @PostMapping("/notifications")
    public ResponseEntity<ApiResponse<Notification>> createOrMarkNotification(
            @AuthenticationPrincipal User user,
            @RequestBody Notification req) {
        return ResponseEntity.ok(ApiResponse.ok(
                automationService.createOrMarkReadNotification(user.getId(), req), "Done"));
    }
}
