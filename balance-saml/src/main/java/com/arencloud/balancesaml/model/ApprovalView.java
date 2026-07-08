package com.arencloud.balancesaml.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ApprovalView(
        long id,
        long accountId,
        String accountNumber,
        String requestedBy,
        Instant requestedAt,
        BigDecimal amount,
        String reason,
        String status,
        String displayStatus,
        String statusClass,
        String decidedBy,
        Instant decidedAt,
        String decisionNote) {
}
