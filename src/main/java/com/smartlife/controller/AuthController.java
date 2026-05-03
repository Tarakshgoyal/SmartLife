package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.User;
import com.smartlife.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", body.get("fullName"));
        Map<String, Object> data = authService.register(
                name, body.get("email"), body.get("password"));
        return ResponseEntity.ok(ApiResponse.ok(data, "Registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.login(body.get("email"), body.get("password"));
        return ResponseEntity.ok(ApiResponse.ok(data, "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal User user) {
        authService.logout(user);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@RequestBody Map<String, String> body) {
        Map<String, Object> data = authService.refresh(body.get("refreshToken"));
        return ResponseEntity.ok(ApiResponse.ok(data, "Token refreshed"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", user.getId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
