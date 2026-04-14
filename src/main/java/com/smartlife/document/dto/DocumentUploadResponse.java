package com.smartlife.document.dto;

import java.util.UUID;

public record DocumentUploadResponse(UUID documentId, String title, String message) {}
