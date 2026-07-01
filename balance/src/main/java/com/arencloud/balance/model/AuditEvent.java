package com.arencloud.balance.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends PanacheEntity {
    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public String actor;

    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public String target;

    @Column(nullable = false, length = 1200)
    public String details;

    public static void record(String actor, String action, String target, String details) {
        AuditEvent event = new AuditEvent();
        event.createdAt = Instant.now();
        event.actor = actor;
        event.action = action;
        event.target = target;
        event.details = details;
        event.persist();
    }
}
