package com.smartlife.service;

import com.smartlife.model.Document;
import com.smartlife.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final OllamaService ollamaService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public Page<Document> getDocuments(Long userId, Document.DocumentType type, Pageable pageable) {
        if (type != null) {
            return documentRepository.findByUserIdAndDocumentType(userId, type, pageable);
        }
        return documentRepository.findByUserId(userId, pageable);
    }

    public Document upload(Long userId, MultipartFile file, String title, String notes,
                           Document.DocumentType documentType) throws IOException {
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "upload";
        String storedName = UUID.randomUUID() + "_" + originalName;
        Path dest = dir.resolve(storedName);
        // Use InputStream copy — more reliable than transferTo() on Windows
        try (var in = file.getInputStream()) {
            java.nio.file.Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Document doc = Document.builder()
                .title(title)
                .notes(notes)
                .documentType(documentType)
                .fileName(file.getOriginalFilename())
                .filePath(dest.toAbsolutePath().toString())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedAt(LocalDateTime.now())
                .userId(userId)
                .build();
        return documentRepository.save(doc);
    }

    public Document getById(Long id, Long userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    public void delete(Long id, Long userId) {
        Document doc = getById(id, userId);
        File file = new File(doc.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        documentRepository.delete(doc);
    }

    public Page<Document> search(Long userId, String q, Pageable pageable) {
        return documentRepository.searchByUserIdAndQuery(userId, q, pageable);
    }

    public List<Document> getExpiring(Long userId, int daysAhead) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusDays(daysAhead);
        return documentRepository.findExpiring(userId, now, cutoff);
    }

    public String medicalParse(Long id, Long userId) {
        Document doc = getById(id, userId);
        try {
            Tika tika = new Tika();
            String text = tika.parseToString(new File(doc.getFilePath()));
            if (text == null || text.isBlank()) {
                text = "Document content could not be extracted.";
            }
            return ollamaService.generate(text);
        } catch (Exception e) {
            return ollamaService.generate("Unable to extract text from document: " + doc.getFileName());
        }
    }
}
