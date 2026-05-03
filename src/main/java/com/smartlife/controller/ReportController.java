package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    @GetMapping("/life-score")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lifeScore(@AuthenticationPrincipal User user) {
        Map<String, Object> breakdown = Map.of(
                "health", 80,
                "finance", 70,
                "documents", 75
        );
        Map<String, Object> data = Map.of(
                "score", 75,
                "breakdown", breakdown
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
