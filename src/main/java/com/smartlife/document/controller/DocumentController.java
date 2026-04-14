package com.smartlife.document.controller;

import com.smartlife.auth.model.User;
import com.smartlife.common.dto.ApiResponse;
import com.smartlife.document.dto.DocumentDto;
import com.smartlife.document.dto.DocumentUploadResponse;
import com.smartlife.document.dto.MedicalReportDto;
import com.smartlife.document.model.DocumentType;
import com.smartlife.document.service.DocumentService;
import com.smartlife.document.service.MedicalDocumentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final MedicalDocumentParser medicalDocumentParser;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal User user) throws IOException {

        DocumentUploadResponse response = documentService.uploadDocument(file, title, notes, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Document uploaded successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> getDocuments(
            @RequestParam(required = false) DocumentType type,
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            @AuthenticationPrincipal User user) {

        Page<DocumentDto> docs = documentService.getUserDocuments(user.getId(), type, pageable);
        return ResponseEntity.ok(ApiResponse.success(docs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(documentService.getDocument(id, user.getId())));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "query", required = false) String query,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user) {

        String searchTerm = q != null ? q : (query != null ? query : "");
        if (searchTerm.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(Page.empty(pageable), "No query provided"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    documentService.searchDocuments(user.getId(), searchTerm, pageable)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(Page.empty(pageable),
                    "Search unavailable (Elasticsearch offline)"));
        }
    }

    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> getExpiring(
            @RequestParam(defaultValue = "30") int daysAhead,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(ApiResponse.success(
                documentService.getExpiringDocuments(user.getId(), daysAhead),
                "Documents expiring in the next " + daysAhead + " days"));
    }

    /**
     * Parse a stored medical/lab report document and return extracted lab values.
     * The document must already be uploaded and OCR-processed.
     */
    @GetMapping("/{id}/medical-parse")
    public ResponseEntity<ApiResponse<MedicalReportDto>> parseMedical(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        var doc = documentService.getDocumentEntity(id, user.getId());
        if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("Document has not been OCR-processed yet"));
        }
        MedicalReportDto report = medicalDocumentParser.parse(doc.getExtractedText());
        return ResponseEntity.ok(ApiResponse.success(report, "Medical report parsed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        documentService.deleteDocument(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted"));
    }
}
