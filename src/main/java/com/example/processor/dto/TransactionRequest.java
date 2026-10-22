package com.example.processor.dto;

import com.example.processor.model.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransactionRequest(
    @NotBlank @Size(max = 100) String transactionId,
    @NotBlank @Size(max = 100) String requestId,
    @NotBlank @Size(max = 100) String accountId,
    @NotNull TransactionType transactionType,
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @Positive long sequenceNumber,
    @Min(0) @Max(3) Integer simulateTransientFailures
) {}
