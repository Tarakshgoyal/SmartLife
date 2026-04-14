package com.smartlife.health.controller;

import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import com.smartlife.health.dto.*;
import com.smartlife.health.service.HealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<HealthLogDto>> log(
            @Valid @RequestBody HealthLogRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(healthService.logHealth(request, user), "Health log saved"));
    }

    @PutMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<HealthLogDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody HealthLogRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(healthService.updateLog(id, request, user)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<HealthLogDto>>> getLogs(
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(healthService.getLogs(user.getId(), pageable)));
    }

    @GetMapping("/logs/range")
    public ResponseEntity<ApiResponse<List<HealthLogDto>>> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(healthService.getDateRange(user.getId(), from, to)));
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<HealthInsightsDto>> getInsights(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                healthService.getInsights(user.getId(), days),
                "Health insights for the past " + days + " days"));
    }

    @DeleteMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        healthService.deleteLog(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Health log deleted"));
    }
}
