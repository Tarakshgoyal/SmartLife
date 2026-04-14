package com.smartlife.expense.model;

import com.smartlife.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expense_user_id", columnList = "user_id"),
        @Index(name = "idx_expense_date", columnList = "expense_date"),
        @Index(name = "idx_expense_category", columnList = "category")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(length = 255)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false)
    private LocalDate expenseDate;

    // For recurring subscriptions
    private boolean isRecurring;
    private String recurringInterval; // MONTHLY, WEEKLY, YEARLY

    // Anomaly detection flag
    private boolean isAnomaly;
    private String anomalyReason;

    // Receipt link
    private UUID receiptDocumentId;

    // Source: MANUAL, RECEIPT_SCAN, BANK_IMPORT
    @Column(length = 50)
    private String source;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (source == null) source = "MANUAL";
    }
}
