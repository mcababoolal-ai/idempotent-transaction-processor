package com.example.processor.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries",
       uniqueConstraints = @UniqueConstraint(name = "uk_ledger_transaction", columnNames = "transaction_id"))
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private TransactionType entryType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(String transactionId, String accountId, BigDecimal amount, TransactionType entryType) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.entryType = entryType;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getEntryType() { return entryType; }
    public Instant getCreatedAt() { return createdAt; }
}
