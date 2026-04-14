package com.smartlife.document.repository;

import com.smartlife.document.model.Document;
import com.smartlife.document.model.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByUserId(UUID userId, Pageable pageable);

    Page<Document> findByUserIdAndType(UUID userId, DocumentType type, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId " +
           "AND d.expiryDate BETWEEN :from AND :to ORDER BY d.expiryDate ASC")
    List<Document> findExpiringDocuments(@Param("userId") UUID userId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId " +
           "AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Document> searchDocuments(@Param("userId") UUID userId,
                                   @Param("query") String query,
                                   Pageable pageable);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndType(UUID userId, DocumentType type);

    @Query("SELECT d FROM Document d WHERE d.expiryDate BETWEEN :from AND :to AND d.processed = true")
    List<Document> findAllExpiringDocuments(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
