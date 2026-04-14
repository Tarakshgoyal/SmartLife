package com.smartlife.automation.controller;

import com.smartlife.auth.model.User;
import com.smartlife.automation.dto.ReminderCreateRequest;
import com.smartlife.automation.dto.ReminderDto;
import com.smartlife.automation.service.NotificationService;
import com.smartlife.automation.service.ReminderService;
import com.smartlife.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Automation", description = "Reminders, scheduled notifications, and real-time alerts")
public class AutomationController {

    private final ReminderService reminderService;
    private final NotificationService notificationService;

    @Operation(summary = "Create a reminder")
    @PostMapping("/reminders")
    public ResponseEntity<ApiResponse<ReminderDto>> createReminder(
            @Valid @RequestBody ReminderCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reminderService.createReminder(request, user), "Reminder created"));
    }

    @Operation(summary = "Get all pending reminders for the current user")
    @GetMapping("/reminders")
    public ResponseEntity<ApiResponse<List<ReminderDto>>> getReminders(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(reminderService.getUserReminders(user.getId())));
    }

    @Operation(summary = "Delete a reminder")
    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        reminderService.deleteReminder(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Reminder deleted"));
    }

    @Operation(summary = "Get in-app notifications (stored in Redis)")
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationService.Notification>>> getNotifications(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(user.getId())));
    }

    @Operation(summary = "Clear all notifications")
    @DeleteMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> clearNotifications(
            @AuthenticationPrincipal User user) {
        notificationService.clearNotifications(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Notifications cleared"));
    }
}
