package com.arencloud.balance.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends PanacheEntity {
    @Column(nullable = false)
    public Long accountId;

    @Column(nullable = false)
    public String requestedBy;

    @Column(nullable = false)
    public Instant requestedAt;

    public String approvedBy;
    public Instant approvedAt;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false, length = 1200)
    public String reason;
}
