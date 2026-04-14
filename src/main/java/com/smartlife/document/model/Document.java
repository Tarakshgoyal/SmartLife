package com.smartlife.document.model;

import com.smartlife.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_doc_user_id", columnList = "user_id"),
        @Index(name = "idx_doc_type", columnList = "document_type"),
        @Index(name = "idx_doc_expiry", columnList = "expiry_date")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(length = 50)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "document_type")
    private DocumentType type;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    // Key-value pairs extracted by NER (stored as JSON string)
    @Column(columnDefinition = "TEXT")
    private String extractedEntities;

    private LocalDate expiryDate;

    private LocalDate issueDate;

    @Column(length = 500)
    private String tags;

    @Column(length = 255)
    private String title;

    @Column(length = 500)
    private String notes;

    private boolean processed;

    private Double classificationConfidence;

    @Column(updatable = false)
    private LocalDateTime uploadedAt;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        processed = false;
    }
}
