package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.HealthLog;
import com.smartlife.model.User;
import com.smartlife.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLogs(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        Page<HealthLog> page = healthService.getLogs(user.getId(), pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("number", page.getNumber());
        result.put("size", page.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/logs/range")
    public ResponseEntity<ApiResponse<List<HealthLog>>> getLogsInRange(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        return ResponseEntity.ok(ApiResponse.ok(healthService.getLogsInRange(user.getId(), from, to)));
    }

    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<HealthLog>> create(
            @AuthenticationPrincipal User user,
            @RequestBody HealthLog req) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        return ResponseEntity.ok(ApiResponse.ok(healthService.create(user.getId(), req), "Created"));
    }

    @PutMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<HealthLog>> update(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody HealthLog req) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        return ResponseEntity.ok(ApiResponse.ok(healthService.update(id, user.getId(), req), "Updated"));
    }

    @DeleteMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        healthService.delete(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<Map<String, Object>>> insights(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "7") int days) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        return ResponseEntity.ok(ApiResponse.ok(healthService.getInsights(user.getId(), days)));
    }
}
