package com.example.processor.service;

import com.example.processor.dto.*;
import com.example.processor.model.*;
import com.example.processor.repository.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class TransactionProcessingService {
    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final List<TransactionStatus> WAITING =
        List.of(TransactionStatus.RECEIVED, TransactionStatus.PENDING, TransactionStatus.RETRY_PENDING);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final EntityManager entityManager;

    public TransactionProcessingService(TransactionRepository transactions,
            AccountRepository accounts, LedgerRepository ledger, EntityManager entityManager) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.ledger = ledger;
        this.entityManager = entityManager;
    }

    @Transactional
    public TransactionResponse ingest(TransactionRequest request) {
        Optional<TransactionRecord> existing = transactions.findById(request.transactionId());
        if (existing.isPresent()) {
            TransactionRecord prior = existing.get();
            if (!samePayload(prior, request)) {
                throw new IdempotencyConflictException(
                    "transactionId already exists with a different business payload");
            }
            log.info("Duplicate delivery transactionId={} status={}",
                prior.getTransactionId(), prior.getStatus());
            return TransactionResponse.from(prior);
        }

        if (accounts.existsById(request.accountId())) {
            Account account = accounts.findById(request.accountId()).orElseThrow();
            if (!account.getCurrency().equals(request.currency())) {
                throw new IllegalArgumentException("Currency does not match account currency");
            }
        }

        TransactionRecord record = new TransactionRecord(
            request.transactionId(), request.requestId(), request.accountId(),
            request.transactionType(), request.amount(), request.currency(),
            request.sequenceNumber(),
            request.simulateTransientFailures() == null ? 0 : request.simulateTransientFailures());

        try {
            transactions.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotencyConflictException(
                "Transaction ID or account sequence already exists; inspect existing transaction");
        }

        ensureAccountExists(request.accountId(), request.currency());
        processAccountAvailable(request.accountId());
        return TransactionResponse.from(transactions.findById(request.transactionId()).orElseThrow());
    }

    @Transactional
    public int processAccountAvailable(String accountId) {
        int processed = 0;
        boolean madeProgress;
        do {
            madeProgress = false;
            Account account = accounts.findByIdForUpdate(accountId).orElseThrow();
            long expected = account.getLastProcessedSequence() + 1;
            List<TransactionRecord> candidates =
                transactions.findByAccountIdAndStatusInOrderBySequenceNumberAsc(accountId, WAITING);

            TransactionRecord next = candidates.stream()
                .filter(t -> t.getSequenceNumber() == expected)
                .findFirst().orElse(null);

            if (next == null) {
                for (TransactionRecord candidate : candidates) {
                    if (candidate.getSequenceNumber() > expected) {
                        candidate.setStatus(TransactionStatus.PENDING);
                    }
                }
                return processed;
            }

            if (next.getSimulatedFailuresRemaining() > 0) {
                int remaining = next.getSimulatedFailuresRemaining() - 1;
                next.setSimulatedFailuresRemaining(remaining);
                next.setRetryCount(next.getRetryCount() + 1);
                next.setLastError("Simulated transient failure");
                if (next.getRetryCount() >= MAX_ATTEMPTS) {
                    next.setStatus(TransactionStatus.FAILED);
                    log.warn("Transaction exhausted retries transactionId={}", next.getTransactionId());
                } else {
                    next.setStatus(TransactionStatus.RETRY_PENDING);
                    log.warn("Retryable failure transactionId={} attempt={}",
                        next.getTransactionId(), next.getRetryCount());
                }
                // A failed sequence blocks later events; recovery must be explicit.
                return processed;
            }

            next.setStatus(TransactionStatus.PROCESSING);
            BigDecimal delta = switch (next.getTransactionType()) {
                case CREDIT -> next.getAmount();
                case DEBIT -> next.getAmount().negate();
                case REVERSAL -> next.getAmount().negate();
            };

            if (delta.signum() < 0 && account.getBalance().compareTo(delta.abs()) < 0) {
                next.setStatus(TransactionStatus.FAILED);
                next.setLastError("Insufficient funds");
                log.warn("Transaction rejected for insufficient funds transactionId={}", next.getTransactionId());
                return processed;
            }

            // All effects are committed together: balance, ledger, transaction status, sequence.
            if (ledger.existsByTransactionId(next.getTransactionId())) {
                next.setStatus(TransactionStatus.PROCESSED);
                account.setLastProcessedSequence(next.getSequenceNumber());
            } else {
                account.apply(delta);
                ledger.save(new LedgerEntry(next.getTransactionId(), next.getAccountId(),
                    next.getAmount(), next.getTransactionType()));
                next.setStatus(TransactionStatus.PROCESSED);
                next.setLastError(null);
                account.setLastProcessedSequence(next.getSequenceNumber());
            }
            transactions.save(next);
            accounts.save(account);
            entityManager.flush();
            processed++;
            madeProgress = true;
            log.info("Processed transactionId={} accountId={} sequence={} status=PROCESSED",
                next.getTransactionId(), next.getAccountId(), next.getSequenceNumber());
        } while (madeProgress);
        return processed;
    }

    @Transactional
    public ProcessingSummary processAll() {
        List<TransactionRecord> waiting = transactions.findByStatusInOrderByCreatedAtAsc(WAITING);
        Set<String> accountIds = new LinkedHashSet<>();
        waiting.forEach(tx -> accountIds.add(tx.getAccountId()));
        int processed = 0;
        int retryAttempts = 0;
        for (String accountId : accountIds) {
            int before = transactions.findByAccountIdAndStatusInOrderBySequenceNumberAsc(accountId, WAITING)
                .stream().mapToInt(TransactionRecord::getRetryCount).sum();
            processed += processAccountAvailable(accountId);
            int after = transactions.findByAccountIdAndStatusInOrderBySequenceNumberAsc(accountId, WAITING)
                .stream().mapToInt(TransactionRecord::getRetryCount).sum();
            retryAttempts += Math.max(0, after - before);
        }
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus status : TransactionStatus.values()) {
            long count = transactions.countByStatus(status);
            if (count > 0) counts.put(status, count);
        }
        return new ProcessingSummary(waiting.size(), processed, retryAttempts, counts);
    }

    @Transactional(readOnly = true)
    public ProcessingSummary getSummary() {
        Map<TransactionStatus, Long> counts = new EnumMap<>(TransactionStatus.class);
        int total = 0;
        for (TransactionStatus status : TransactionStatus.values()) {
            long count = transactions.countByStatus(status);
            if (count > 0) counts.put(status, count);
            total += (int) count;
        }
        return new ProcessingSummary(total, 0, 0, counts);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String id) {
        return transactions.findById(id).map(TransactionResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String id) {
        return accounts.findById(id).map(AccountResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getLedger(String accountId) {
        return ledger.findByAccountIdOrderByIdAsc(accountId);
    }

    private void ensureAccountExists(String accountId, String currency) {
        if (!accounts.existsById(accountId)) {
            try {
                accounts.saveAndFlush(new Account(accountId, currency));
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent request created it; next transaction will read it.
            }
        }
    }

    private boolean samePayload(TransactionRecord prior, TransactionRequest request) {
        return prior.getAccountId().equals(request.accountId())
            && prior.getTransactionType() == request.transactionType()
            && prior.getAmount().compareTo(request.amount()) == 0
            && prior.getCurrency().equals(request.currency())
            && prior.getSequenceNumber() == request.sequenceNumber();
    }
}
