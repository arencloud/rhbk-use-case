package com.arencloud.balancesaml.model;

import java.time.Instant;

public record AuditView(
        Instant createdAt,
        String actor,
        String action,
        String target,
        String details) {
}
