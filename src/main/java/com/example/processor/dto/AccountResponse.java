package com.example.processor.dto;

import com.example.processor.model.Account;
import java.math.BigDecimal;

public record AccountResponse(
    String accountId,
    String currency,
    BigDecimal balance,
    long lastProcessedSequence
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getAccountId(), account.getCurrency(),
            account.getBalance(), account.getLastProcessedSequence());
    }
}
