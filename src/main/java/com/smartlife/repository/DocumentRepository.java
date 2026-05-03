package com.smartlife.repository;

import com.smartlife.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByUserId(Long userId, Pageable pageable);

    Page<Document> findByUserIdAndDocumentType(Long userId, Document.DocumentType type, Pageable pageable);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT d FROM Document d WHERE d.userId = :userId AND " +
           "(LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(d.notes) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Document> searchByUserIdAndQuery(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.userId = :userId AND d.expiryDate IS NOT NULL AND d.expiryDate BETWEEN :now AND :cutoff")
    List<Document> findExpiring(@Param("userId") Long userId,
                                @Param("now") LocalDateTime now,
                                @Param("cutoff") LocalDateTime cutoff);

    long countByUserId(Long userId);
}
