package com.example.processor.dto;

import com.example.processor.model.TransactionRecord;
import com.example.processor.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    String transactionId,
    String accountId,
    String transactionType,
    BigDecimal amount,
    String currency,
    long sequenceNumber,
    TransactionStatus status,
    int retryCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
    public static TransactionResponse from(TransactionRecord tx) {
        return new TransactionResponse(tx.getTransactionId(), tx.getAccountId(),
            tx.getTransactionType().name(), tx.getAmount(), tx.getCurrency(),
            tx.getSequenceNumber(), tx.getStatus(), tx.getRetryCount(),
            tx.getLastError(), tx.getCreatedAt(), tx.getUpdatedAt());
    }
}
