package com.smartlife.analytics.controller;

import com.smartlife.analytics.dto.DashboardDto;
import com.smartlife.analytics.service.AnalyticsService;
import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-module aggregated dashboard and life metrics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get aggregated dashboard (expenses, health, documents, reminders)")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardDto>> getDashboard(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDashboard(user)));
    }
}
