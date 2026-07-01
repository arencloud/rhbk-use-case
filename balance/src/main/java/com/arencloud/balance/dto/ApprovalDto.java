package com.arencloud.balance.dto;

import com.arencloud.balance.model.ApprovalRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record ApprovalDto(
        Long id,
        Long accountId,
        String requestedBy,
        Instant requestedAt,
        String approvedBy,
        Instant approvedAt,
        BigDecimal amount,
        String status,
        String reason) {

    public static ApprovalDto from(ApprovalRequest request) {
        return new ApprovalDto(
                request.id,
                request.accountId,
                request.requestedBy,
                request.requestedAt,
                request.approvedBy,
                request.approvedAt,
                request.amount,
                request.status,
                request.reason);
    }
}
