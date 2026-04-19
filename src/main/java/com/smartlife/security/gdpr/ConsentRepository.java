package com.smartlife.security.gdpr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByUserIdOrderByGrantedAtDesc(UUID userId);

    Optional<ConsentRecord> findByUserIdAndPurposeAndGrantedTrue(UUID userId, String purpose);

    boolean existsByUserIdAndPurposeAndGrantedTrue(UUID userId, String purpose);

    List<ConsentRecord> findByUserIdAndGrantedTrue(UUID userId);
}
