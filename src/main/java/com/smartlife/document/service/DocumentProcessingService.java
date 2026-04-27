package com.smartlife.document.service;

import com.smartlife.common.exception.SmartLifeException;
import com.smartlife.document.model.Document;
import com.smartlife.document.model.DocumentType;
import com.smartlife.document.repository.DocumentRepository;
import com.smartlife.document.search.DocumentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Separate bean to hold the @Async document processing logic.
 * Must be a distinct Spring bean so the proxy is used — self-invocation
 * from DocumentService would bypass @Async entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final OcrService ocrService;
    private final DocumentClassificationService classificationService;
    private final EntityExtractionService entityExtractionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DocumentIndexingService documentIndexingService;

    @Async
    public void processDocumentAsync(UUID documentId, String storagePath, String documentProcessedTopic) {
        Document document = null;
        try {
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new SmartLifeException("Document not found", HttpStatus.NOT_FOUND));

            // Step 1: OCR — returns "" if Tesseract unavailable or file type unsupported
            String extractedText = ocrService.extractText(Paths.get(storagePath));
            document.setExtractedText(extractedText);

            // Step 2: Classify — works even on empty text (returns UNKNOWN)
            DocumentClassificationService.DocumentClassificationResult result =
                    classificationService.classify(extractedText);
            document.setType(result.type());
            document.setClassificationConfidence(result.confidence());

            // Step 3: Extract entities — skip on empty text
            if (extractedText != null && !extractedText.isBlank()) {
                EntityExtractionService.ExtractionResult extraction =
                        entityExtractionService.extract(extractedText);
                document.setExtractedEntities(entityExtractionService.toJson(extraction.entities()));
                document.setExpiryDate(extraction.expiryDate());
                document.setIssueDate(extraction.issueDate());
            }

            document.setProcessed(true);
            document.setProcessedAt(LocalDateTime.now());
            documentRepository.save(document);

            // Index in Elasticsearch
            try {
                documentIndexingService.indexDocument(document);
            } catch (Exception e) {
                log.warn("Elasticsearch indexing failed for document {}: {}", documentId, e.getMessage());
            }

            // Publish Kafka event
            try {
                kafkaTemplate.send(documentProcessedTopic, documentId.toString(),
                        new DocumentProcessedEvent(documentId, document.getType(),
                                document.getExpiryDate(), document.getUser().getId()));
            } catch (Exception e) {
                log.warn("Kafka unavailable — document event not published: {}", e.getMessage());
            }

            log.info("Document {} processed: type={}, expiry={}", documentId, result.type(), document.getExpiryDate());

        } catch (Throwable e) {
            log.error("Failed to process document {}: {}", documentId, e.getMessage(), e);
            if (document != null) {
                try {
                    document.setProcessed(true);
                    document.setProcessedAt(LocalDateTime.now());
                    documentRepository.save(document);
                } catch (Exception saveEx) {
                    log.error("Could not mark document {} as processed after failure", documentId, saveEx);
                }
            }
        }
    }

    public record DocumentProcessedEvent(UUID documentId, DocumentType type,
                                          LocalDate expiryDate, UUID userId) {}
}
