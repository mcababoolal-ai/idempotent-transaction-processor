package com.example.processor.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "last_processed_sequence", nullable = false)
    private long lastProcessedSequence;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {}

    public Account(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.lastProcessedSequence = 0;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void touch() { this.updatedAt = Instant.now(); }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public long getLastProcessedSequence() { return lastProcessedSequence; }

    public void apply(BigDecimal delta) { this.balance = this.balance.add(delta); }
    public void setLastProcessedSequence(long value) { this.lastProcessedSequence = value; }
}
