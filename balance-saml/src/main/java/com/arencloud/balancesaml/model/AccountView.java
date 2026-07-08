package com.arencloud.balancesaml.model;

import java.math.BigDecimal;

public record AccountView(
        long id,
        String accountNumber,
        String customerName,
        String product,
        String currency,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        String riskBand,
        String status) {
}
