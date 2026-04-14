package com.smartlife.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String email,
        String fullName,
        String role
) {
    public static AuthResponse of(String accessToken, String refreshToken, UUID userId,
                                  String email, String fullName, String role) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", userId, email, fullName, role);
    }
}
