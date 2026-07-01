package com.arencloud.balance.dto;

import com.arencloud.balance.model.AuditEvent;

import java.time.Instant;

public record AuditEventDto(
        Long id,
        Instant createdAt,
        String actor,
        String action,
        String target,
        String details) {

    public static AuditEventDto from(AuditEvent event) {
        return new AuditEventDto(event.id, event.createdAt, event.actor, event.action, event.target, event.details);
    }
}
