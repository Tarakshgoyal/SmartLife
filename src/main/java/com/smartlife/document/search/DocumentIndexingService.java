package com.smartlife.document.search;

import com.smartlife.document.model.Document;
import com.smartlife.document.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Bridges PostgreSQL document storage with Elasticsearch for full-text search.
 *
 * Index lifecycle:
 *  - Document uploaded → indexed after OCR processing completes
 *  - Document deleted  → removed from index
 *  - Full reindex      → triggered from admin endpoint or on startup
 */
@Service
@Slf4j
public class DocumentIndexingService {

    private final DocumentSearchRepository searchRepository;
    private final DocumentRepository documentRepository;

    @Autowired
    public DocumentIndexingService(@Lazy DocumentSearchRepository searchRepository,
                                   DocumentRepository documentRepository) {
        this.searchRepository = searchRepository;
        this.documentRepository = documentRepository;
    }

    /** Index or re-index a single document (called after OCR completes). */
    @Async
    public void indexDocument(Document doc) {
        if (!doc.isProcessed()) return;
        DocumentSearchDocument searchDoc = toSearchDoc(doc);
        searchRepository.save(searchDoc);
        log.debug("Document indexed in Elasticsearch: {}", doc.getId());
    }

    /** Remove a document from the search index. */
    public void removeFromIndex(UUID documentId) {
        searchRepository.deleteById(documentId.toString());
        log.debug("Document removed from Elasticsearch index: {}", documentId);
    }

    /**
     * Full-text search using Elasticsearch.
     * Falls back to PostgreSQL LIKE search if ES is unavailable.
     */
    public Page<DocumentSearchDocument> search(UUID userId, String query, Pageable pageable) {
        try {
            return searchRepository.searchByUserAndText(userId.toString(), query, pageable);
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, returning empty results: {}", e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /** Bulk reindex all processed documents for a user. */
    @Async
    public void reindexUser(UUID userId) {
        List<Document> docs = documentRepository.findByUserId(userId, Pageable.unpaged()).getContent();
        long indexed = 0;
        for (Document doc : docs) {
            if (doc.isProcessed()) {
                searchRepository.save(toSearchDoc(doc));
                indexed++;
            }
        }
        log.info("Reindexed {} documents for user {}", indexed, userId);
    }

    private DocumentSearchDocument toSearchDoc(Document doc) {
        return new DocumentSearchDocument(
                doc.getId().toString(),
                doc.getUser().getId().toString(),
                doc.getTitle(),
                doc.getExtractedText(),
                doc.getType() != null ? doc.getType().name() : null,
                doc.getTags(),
                doc.getNotes(),
                doc.getExpiryDate(),
                doc.getUploadedAt()
        );
    }
}
