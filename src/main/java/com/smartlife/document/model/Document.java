package com.smartlife.document.model;

import com.smartlife.auth.model.User;
import com.smartlife.security.encryption.FieldEncryptor;
import com.smartlife.security.gdpr.Sensitive;
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

    // OCR text — encrypted because documents may contain PII (passport numbers, medical data, etc.)
    @Sensitive(strategy = Sensitive.MaskingStrategy.FULL, label = "Document OCR Text")
    @Convert(converter = FieldEncryptor.class)
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    // Key-value pairs extracted by NER — encrypted (may contain names, IDs, amounts)
    @Sensitive(strategy = Sensitive.MaskingStrategy.FULL, label = "Extracted Entities")
    @Convert(converter = FieldEncryptor.class)
    @Column(columnDefinition = "TEXT")
    private String extractedEntities;

    private LocalDate expiryDate;

    private LocalDate issueDate;

    @Column(length = 500)
    private String tags;

    @Column(length = 255)
    private String title;

    @Sensitive(strategy = Sensitive.MaskingStrategy.FULL, label = "Document Notes")
    @Convert(converter = FieldEncryptor.class)
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
