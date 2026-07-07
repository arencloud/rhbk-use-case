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
        String displayStatus,
        String statusClass,
        String reason) {

    private static String displayStatus(String status) {
        return switch (status) {
            case "PENDING" -> "Requested";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            default -> status;
        };
    }

    private static String statusClass(String status) {
        return switch (status) {
            case "PENDING" -> "requested";
            case "APPROVED" -> "approved";
            case "REJECTED" -> "rejected";
            default -> "unknown";
        };
    }

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
                displayStatus(request.status),
                statusClass(request.status),
                request.reason);
    }
}
