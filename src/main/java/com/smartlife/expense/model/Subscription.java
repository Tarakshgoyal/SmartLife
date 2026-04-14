package com.smartlife.expense.model;

import com.smartlife.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_sub_user_id", columnList = "user_id"),
        @Index(name = "idx_sub_active", columnList = "active")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;            // e.g. "Netflix"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String frequency;       // MONTHLY, YEARLY, WEEKLY

    private LocalDate firstChargeDate;
    private LocalDate lastChargeDate;
    private LocalDate nextExpectedDate;

    private int chargeCount;        // how many times detected
    private boolean active;

    private boolean markedUnused;   // user flagged as no longer used

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
