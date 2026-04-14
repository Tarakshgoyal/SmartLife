package com.smartlife.analytics.controller;

import com.smartlife.analytics.dto.LifeScoreDto;
import com.smartlife.analytics.dto.WeeklyReportDto;
import com.smartlife.analytics.service.LifeReportService;
import com.smartlife.analytics.service.LifeScoreService;
import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final LifeReportService lifeReportService;
    private final LifeScoreService lifeScoreService;

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<WeeklyReportDto>> getWeeklyReport(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                lifeReportService.generateWeeklyReport(user.getId()),
                "Weekly life report generated"));
    }

    @GetMapping("/life-score")
    public ResponseEntity<ApiResponse<LifeScoreDto>> getLifeScore(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                lifeScoreService.computeScore(user.getId()),
                "Life score computed"));
    }
}
