package com.smartlife.controller;

import com.smartlife.common.ApiResponse;
import com.smartlife.model.Document;
import com.smartlife.model.User;
import com.smartlife.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 100, sort = "id") Pageable pageable) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        Document.DocumentType dt = null;
        if (type != null && !type.isBlank()) {
            try { dt = Document.DocumentType.valueOf(type); } catch (IllegalArgumentException ignored) {}
        }
        Page<Document> page = documentService.getDocuments(user.getId(), dt, pageable);
        // Return as plain map so Jackson can serialize without Page serialization issues
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("number", page.getNumber());
        result.put("size", page.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Document>> upload(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "documentType", defaultValue = "GENERAL") String documentType) throws IOException {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        Document.DocumentType dt;
        try { dt = Document.DocumentType.valueOf(documentType); } catch (IllegalArgumentException e) { dt = Document.DocumentType.GENERAL; }
        Document doc = documentService.upload(user.getId(), file, title, notes, dt);
        return ResponseEntity.ok(ApiResponse.ok(doc, "Document uploaded"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).build();
        Document doc = documentService.getById(id, user.getId());
        File file = new File(doc.getFilePath());
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .contentType(doc.getMimeType() != null
                        ? MediaType.parseMediaType(doc.getMimeType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        documentService.delete(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Document deleted"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(
            @AuthenticationPrincipal User user,
            @RequestParam String q,
            @PageableDefault(size = 50, sort = "id") Pageable pageable) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        Page<Document> page = documentService.search(user.getId(), q, pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("number", page.getNumber());
        result.put("size", page.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<List<Document>>> expiring(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int daysAhead) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        List<Document> docs = documentService.getExpiring(user.getId(), daysAhead);
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    @PostMapping("/{id}/medical-parse")
    public ResponseEntity<ApiResponse<Map<String, String>>> medicalParse(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        String analysis = documentService.medicalParse(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("analysis", analysis)));
    }
}
