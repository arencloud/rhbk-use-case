package com.arencloud.balance.service;

import com.arencloud.balance.model.Account;
import com.arencloud.balance.model.AuditEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

@ApplicationScoped
public class SeedData {

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (Account.count() > 0) {
            return;
        }

        createAccount("AC-100-2048", "Aren Martirosyan", "CHECKING", "USD", "18420.55");
        createAccount("AC-200-4096", "Narine Hakobyan", "SAVINGS", "USD", "92350.10");
        createAccount("AC-300-8192", "Tigran Sargsyan", "BUSINESS", "EUR", "241908.77");
        AuditEvent.record("system", "SEED_DATA", "accounts", "Initial lab data created");
    }

    private void createAccount(String number, String customer, String type, String currency, String balance) {
        Account account = new Account();
        account.accountNumber = number;
        account.customerName = customer;
        account.type = type;
        account.currency = currency;
        account.balance = new BigDecimal(balance);
        account.status = "ACTIVE";
        account.persist();
    }
}
