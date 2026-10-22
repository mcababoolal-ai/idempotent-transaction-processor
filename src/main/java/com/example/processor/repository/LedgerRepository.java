package com.example.processor.repository;

import com.example.processor.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByTransactionId(String transactionId);
    List<LedgerEntry> findByAccountIdOrderByIdAsc(String accountId);
}
