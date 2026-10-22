package com.example.processor.model;

public enum TransactionStatus {
    RECEIVED, PROCESSING, PROCESSED, DUPLICATE, PENDING,
    RETRY_PENDING, FAILED, REJECTED
}
