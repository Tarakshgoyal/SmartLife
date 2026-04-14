package com.smartlife.automation.controller;

import com.smartlife.auth.model.User;
import com.smartlife.automation.dto.ReminderCreateRequest;
import com.smartlife.automation.dto.ReminderDto;
import com.smartlife.automation.service.NotificationService;
import com.smartlife.automation.service.ReminderService;
import com.smartlife.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final ReminderService reminderService;
    private final NotificationService notificationService;

    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<ReminderDto>> createReminder(
            @Valid @RequestBody ReminderCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reminderService.createReminder(request, user), "Reminder created"));
    }

    @GetMapping("/reminders")
    public ResponseEntity<ApiResponse<List<ReminderDto>>> getReminders(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(reminderService.getUserReminders(user.getId())));
    }

    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        reminderService.deleteReminder(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Reminder deleted"));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationService.Notification>>> getNotifications(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(user.getId())));
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> clearNotifications(
            @AuthenticationPrincipal User user) {
        notificationService.clearNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Notifications cleared"));
    }
}
