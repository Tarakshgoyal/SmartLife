package com.smartlife.document.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentSearchRepository extends ElasticsearchRepository<DocumentSearchDocument, String> {

    Page<DocumentSearchDocument> findByUserId(String userId, Pageable pageable);

    /**
     * Multi-field full-text search across title, extracted text, tags, and notes.
     */
    @Query("""
            {
              "bool": {
                "must": [
                  {"term": {"userId": "?0"}},
                  {"multi_match": {
                    "query": "?1",
                    "fields": ["title^3", "extractedText", "tags^2", "notes"],
                    "type": "best_fields",
                    "fuzziness": "AUTO"
                  }}
                ]
              }
            }
            """)
    Page<DocumentSearchDocument> searchByUserAndText(String userId, String query, Pageable pageable);

    void deleteByUserId(String userId);
}
