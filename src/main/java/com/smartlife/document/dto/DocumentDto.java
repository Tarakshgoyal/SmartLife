package com.smartlife.document.dto;

import com.smartlife.document.model.Document;
import com.smartlife.document.model.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        String fileName,
        String title,
        DocumentType type,
        double classificationConfidence,
        LocalDate expiryDate,
        LocalDate issueDate,
        String tags,
        String notes,
        boolean processed,
        LocalDateTime uploadedAt,
        LocalDateTime processedAt
) {
    public static DocumentDto from(Document d) {
        return new DocumentDto(
                d.getId(), d.getFileName(), d.getTitle(), d.getType(),
                d.getClassificationConfidence() != null ? d.getClassificationConfidence() : 0.0,
                d.getExpiryDate(), d.getIssueDate(), d.getTags(), d.getNotes(),
                d.isProcessed(), d.getUploadedAt(), d.getProcessedAt()
        );
    }
}
