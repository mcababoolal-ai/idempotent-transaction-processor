package com.example.processor.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions",
       uniqueConstraints = @UniqueConstraint(name = "uk_account_sequence",
           columnNames = {"account_id", "sequence_number"}))
public class TransactionRecord {
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false, length = 100)
    private String transactionId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "simulated_failures_remaining", nullable = false)
    private int simulatedFailuresRemaining;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionRecord() {}

    public TransactionRecord(String transactionId, String requestId, String accountId,
            TransactionType transactionType, BigDecimal amount, String currency,
            long sequenceNumber, int simulatedFailures) {
        this.transactionId = transactionId;
        this.requestId = requestId;
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
        this.status = TransactionStatus.RECEIVED;
        this.retryCount = 0;
        this.simulatedFailuresRemaining = simulatedFailures;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void touch() { this.updatedAt = Instant.now(); }

    public String getTransactionId() { return transactionId; }
    public String getRequestId() { return requestId; }
    public String getAccountId() { return accountId; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public long getSequenceNumber() { return sequenceNumber; }
    public TransactionStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getSimulatedFailuresRemaining() { return simulatedFailuresRemaining; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setSimulatedFailuresRemaining(int value) { this.simulatedFailuresRemaining = value; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
