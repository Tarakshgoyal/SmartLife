package com.smartlife.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String email,
        String fullName,
        String role,
        LocalDateTime createdAt
) {}
