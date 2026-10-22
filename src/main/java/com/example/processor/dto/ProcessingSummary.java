package com.example.processor.dto;

import com.example.processor.model.TransactionStatus;
import java.util.Map;

public record ProcessingSummary(
    int examined,
    int processedDuringRun,
    int retryAttemptsDuringRun,
    Map<TransactionStatus, Long> countsByStatus
) {}
