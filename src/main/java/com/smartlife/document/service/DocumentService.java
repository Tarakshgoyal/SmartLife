package com.smartlife.document.service;

import com.smartlife.auth.model.User;
import com.smartlife.common.exception.SmartLifeException;
import com.smartlife.document.search.DocumentIndexingService;
import com.smartlife.document.dto.DocumentDto;
import com.smartlife.document.dto.DocumentUploadResponse;
import com.smartlife.document.model.Document;
import com.smartlife.document.model.DocumentType;
import com.smartlife.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final OcrService ocrService;
    private final DocumentClassificationService classificationService;
    private final EntityExtractionService entityExtractionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DocumentIndexingService documentIndexingService;

    @Value("${smartlife.storage.upload-dir}")
    private String uploadDir;

    @Value("${smartlife.kafka.topics.document-processed}")
    private String documentProcessedTopic;

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, String title,
                                                  String notes, User user) throws IOException {
        validateFile(file);

        String storagePath = saveFile(file, user.getId());

        Document document = Document.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .storagePath(storagePath)
                .mimeType(file.getContentType())
                .type(DocumentType.UNKNOWN)
                .title(title != null ? title : file.getOriginalFilename())
                .notes(notes)
                .build();

        documentRepository.save(document);
        log.info("Document uploaded: {} for user {}", document.getId(), user.getEmail());

        // Process asynchronously (OCR + classification)
        processDocumentAsync(document.getId(), storagePath);

        return new DocumentUploadResponse(document.getId(), document.getTitle(),
                "Document uploaded. Processing in progress...");
    }

    @Async
    public void processDocumentAsync(UUID documentId, String storagePath) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new SmartLifeException("Document not found", HttpStatus.NOT_FOUND));

            // Step 1: OCR
            String extractedText = ocrService.extractText(Paths.get(storagePath));
            document.setExtractedText(extractedText);

            // Step 2: Classify
            DocumentClassificationService.DocumentClassificationResult result =
                    classificationService.classify(extractedText);
            document.setType(result.type());
            document.setClassificationConfidence(result.confidence());

            // Step 3: Extract entities
            EntityExtractionService.ExtractionResult extraction =
                    entityExtractionService.extract(extractedText);
            document.setExtractedEntities(entityExtractionService.toJson(extraction.entities()));
            document.setExpiryDate(extraction.expiryDate());
            document.setIssueDate(extraction.issueDate());

            document.setProcessed(true);
            document.setProcessedAt(LocalDateTime.now());
            documentRepository.save(document);

            // Index in Elasticsearch for full-text search
            documentIndexingService.indexDocument(document);

            // Publish event for automation engine
            try {
                kafkaTemplate.send(documentProcessedTopic, documentId.toString(),
                        new DocumentProcessedEvent(documentId, document.getType(),
                                document.getExpiryDate(), document.getUser().getId()));
            } catch (Exception e) {
                log.warn("Kafka unavailable — document event not published: {}", e.getMessage());
            }

            log.info("Document {} processed: type={}, expiry={}", documentId, result.type(), extraction.expiryDate());

        } catch (Exception e) {
            log.error("Failed to process document {}", documentId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> getUserDocuments(UUID userId, DocumentType type, Pageable pageable) {
        Page<Document> page = type != null
                ? documentRepository.findByUserIdAndType(userId, type, pageable)
                : documentRepository.findByUserId(userId, pageable);
        return page.map(DocumentDto::from);
    }

    @Transactional(readOnly = true)
    public DocumentDto getDocument(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .map(DocumentDto::from)
                .orElseThrow(() -> new SmartLifeException("Document not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Document getDocumentEntity(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new SmartLifeException("Document not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> searchDocuments(UUID userId, String query, Pageable pageable) {
        return documentRepository.searchDocuments(userId, query, pageable).map(DocumentDto::from);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> getExpiringDocuments(UUID userId, int daysAhead) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(daysAhead);
        return documentRepository.findExpiringDocuments(userId, from, to)
                .stream().map(DocumentDto::from).toList();
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new SmartLifeException("Document not found", HttpStatus.NOT_FOUND));
        documentRepository.delete(document);
        log.info("Document {} deleted by user {}", documentId, userId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SmartLifeException("File is empty", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new SmartLifeException("Only image and PDF files are supported", HttpStatus.BAD_REQUEST);
        }
    }

    private String saveFile(MultipartFile file, UUID userId) throws IOException {
        Path userDir = Paths.get(uploadDir, userId.toString(), "documents");
        Files.createDirectories(userDir);

        String ext = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + ext;
        Path filePath = userDir.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath.toString();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    public record DocumentProcessedEvent(UUID documentId, DocumentType type,
                                          LocalDate expiryDate, UUID userId) {}
}
