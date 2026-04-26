package com.smartlife.document.dto;

import java.util.UUID;

public record DocumentUploadResponse(UUID id, String title, String message) {}
