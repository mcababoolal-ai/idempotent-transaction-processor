package com.example.processor;

import com.example.processor.dto.TransactionRequest;
import com.example.processor.dto.TransactionResponse;
import com.example.processor.model.TransactionStatus;
import com.example.processor.model.TransactionType;
import com.example.processor.repository.AccountRepository;
import com.example.processor.repository.LedgerRepository;
import com.example.processor.repository.TransactionRepository;
import com.example.processor.service.IdempotencyConflictException;
import com.example.processor.service.TransactionProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransactionProcessorIntegrationTest {
    @Autowired TransactionProcessingService service;
    @Autowired AccountRepository accounts;
    @Autowired LedgerRepository ledger;
    @Autowired TransactionRepository transactions;

    private TransactionRequest request(String txId, String requestId, String account,
            TransactionType type, String amount, long seq, int simulatedFailures) {
        return new TransactionRequest(txId, requestId, account, type, new BigDecimal(amount),
            "INR", seq, simulatedFailures);
    }

    @Test
    void duplicateDeliveryCreatesOnlyOneLedgerEntry() {
        service.ingest(request("T-DUP", "R1", "A-DUP", TransactionType.CREDIT, "100.00", 1, 0));
        service.ingest(request("T-DUP", "R2", "A-DUP", TransactionType.CREDIT, "100.00", 1, 0));
        assertEquals(0, new BigDecimal("100.00").compareTo(accounts.findById("A-DUP").orElseThrow().getBalance()));
        assertEquals(1, ledger.findByAccountIdOrderByIdAsc("A-DUP").size());
    }

    @Test
    void sameIdWithDifferentPayloadIsConflict() {
        service.ingest(request("T-CONFLICT", "R1", "A-CONFLICT", TransactionType.CREDIT, "100.00", 1, 0));
        assertThrows(IdempotencyConflictException.class, () ->
            service.ingest(request("T-CONFLICT", "R2", "A-CONFLICT", TransactionType.CREDIT, "200.00", 1, 0)));
    }

    @Test
    void outOfOrderTransactionWaitsThenProcessesInSequence() {
        TransactionResponse second = service.ingest(
            request("T-2", "R2", "A-ORDER", TransactionType.DEBIT, "40.00", 2, 0));
        assertEquals(TransactionStatus.PENDING, second.status());

        service.ingest(request("T-1", "R1", "A-ORDER", TransactionType.CREDIT, "100.00", 1, 0));
        assertEquals(0, new BigDecimal("60.00").compareTo(accounts.findById("A-ORDER").orElseThrow().getBalance()));
        assertEquals(TransactionStatus.PROCESSED, transactions.findById("T-2").orElseThrow().getStatus());
    }

    @Test
    void transientFailureCanBeRetriedWithoutDuplicateEffect() {
        TransactionResponse first = service.ingest(
            request("T-RETRY", "R1", "A-RETRY", TransactionType.CREDIT, "500.00", 1, 1));
        assertEquals(TransactionStatus.RETRY_PENDING, first.status());
        service.processAll();
        assertEquals(TransactionStatus.PROCESSED, transactions.findById("T-RETRY").orElseThrow().getStatus());
        assertEquals(1, ledger.findByAccountIdOrderByIdAsc("A-RETRY").size());
        assertEquals(0, new BigDecimal("500.00").compareTo(accounts.findById("A-RETRY").orElseThrow().getBalance()));
    }
}
