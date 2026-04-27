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
    private final DocumentProcessingService documentProcessingService;

    @Value("${smartlife.storage.upload-dir}")
    private String uploadDir;

    @Value("${smartlife.kafka.topics.document-processed}")
    private String documentProcessedTopic;

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, String title,
                                                  String notes, User user) throws IOException {
        validateFile(file);
        String originalName = safeOriginalFilename(file);
        String safeTitle = truncate(title != null ? title : originalName, 255);
        String safeNotes = truncate(notes, 500);

        String storagePath = saveFile(file, user.getId());

        Document document = Document.builder()
                .user(user)
                .fileName(truncate(originalName, 255))
                .storagePath(storagePath)
                .mimeType(file.getContentType())
                .type(DocumentType.UNKNOWN)
                .title(safeTitle)
                .notes(safeNotes)
                .build();

        documentRepository.save(document);
        log.info("Document uploaded: {} for user {}", document.getId(), user.getEmail());

        // Process asynchronously via separate bean (avoids @Async self-invocation)
        documentProcessingService.processDocumentAsync(document.getId(), storagePath, documentProcessedTopic);

        return new DocumentUploadResponse(document.getId(), document.getTitle(),
                "Document uploaded successfully. OCR processing in progress...");
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

    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of(
            "application/pdf",
            "application/msword",                                                          // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",    // .docx
            "application/vnd.ms-excel",                                                   // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",          // .xlsx
            "text/plain"
    );

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SmartLifeException("File is empty", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new SmartLifeException("Could not determine file type", HttpStatus.BAD_REQUEST);
        }
        // Accept all images, PDFs, and common Office document formats
        boolean allowed = contentType.startsWith("image/") || ALLOWED_MIME_TYPES.contains(contentType);
        if (!allowed) {
            throw new SmartLifeException(
                    "Unsupported file type: " + contentType + ". Accepted: images, PDF, Word, Excel, text.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String saveFile(MultipartFile file, UUID userId) throws IOException {
        Path userDir = Paths.get(uploadDir, userId.toString(), "documents");
        Files.createDirectories(userDir);

        String ext = getExtension(safeOriginalFilename(file));
        String fileName = UUID.randomUUID() + ext;
        Path filePath = userDir.resolve(fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath.toString();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String safeOriginalFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) return "upload.bin";
        return original.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

}
