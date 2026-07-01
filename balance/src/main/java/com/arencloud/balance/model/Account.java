package com.arencloud.balance.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String accountNumber;

    @Column(nullable = false)
    public String customerName;

    @Column(nullable = false)
    public String type;

    @Column(nullable = false)
    public String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal balance;

    @Column(nullable = false)
    public String status;
}
