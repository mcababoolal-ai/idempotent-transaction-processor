package com.example.processor.repository;

import com.example.processor.model.TransactionRecord;
import com.example.processor.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {
    List<TransactionRecord> findByAccountIdAndStatusInOrderBySequenceNumberAsc(
        String accountId, List<TransactionStatus> statuses);
    List<TransactionRecord> findByStatusInOrderByCreatedAtAsc(List<TransactionStatus> statuses);
    long countByStatus(TransactionStatus status);
}
