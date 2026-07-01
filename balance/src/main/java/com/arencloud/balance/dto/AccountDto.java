package com.arencloud.balance.dto;

import com.arencloud.balance.model.Account;

import java.math.BigDecimal;

public record AccountDto(
        Long id,
        String accountNumber,
        String customerName,
        String type,
        String currency,
        BigDecimal balance,
        String status) {

    public static AccountDto from(Account account) {
        return new AccountDto(
                account.id,
                account.accountNumber,
                account.customerName,
                account.type,
                account.currency,
                account.balance,
                account.status);
    }
}
