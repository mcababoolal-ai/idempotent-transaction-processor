package com.example.processor.controller;

import com.example.processor.dto.*;
import com.example.processor.model.LedgerEntry;
import com.example.processor.service.TransactionProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final TransactionProcessingService service;

    public TransactionController(TransactionProcessingService service) {
        this.service = service;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse ingest(@Valid @RequestBody TransactionRequest request) {
        return service.ingest(request);
    }

    @PostMapping("/transactions/process")
    public ProcessingSummary process() {
        return service.processAll();
    }

    @GetMapping("/transactions/{id}")
    public TransactionResponse getTransaction(@PathVariable String id) {
        return service.getTransaction(id);
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse getAccount(@PathVariable String id) {
        return service.getAccount(id);
    }

    @GetMapping("/accounts/{id}/ledger")
    public List<LedgerEntry> getLedger(@PathVariable String id) {
        return service.getLedger(id);
    }

    @GetMapping("/transactions/summary")
    public ProcessingSummary summary() {
        return service.getSummary();
    }
}
