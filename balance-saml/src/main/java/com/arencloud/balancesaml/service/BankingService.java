package com.arencloud.balancesaml.service;

import com.arencloud.balancesaml.model.AccountView;
import com.arencloud.balancesaml.model.ApprovalView;
import com.arencloud.balancesaml.model.AuditView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class BankingService {
    private final List<AccountView> accounts = List.of(
            new AccountView(1, "AC-SAML-1001", "Aren Martirosyan", "Premier Checking", "USD",
                    new BigDecimal("18420.55"), new BigDecimal("18650.55"), "LOW", "ACTIVE"),
            new AccountView(2, "AC-SAML-2048", "Narine Hakobyan", "Executive Savings", "USD",
                    new BigDecimal("92350.10"), new BigDecimal("92350.10"), "LOW", "ACTIVE"),
            new AccountView(3, "AC-SAML-4096", "Tigran Sargsyan", "Commercial Treasury", "EUR",
                    new BigDecimal("241908.77"), new BigDecimal("248200.00"), "MEDIUM", "WATCH"),
            new AccountView(4, "AC-SAML-8192", "Ani Petrosyan", "Private Wealth", "CHF",
                    new BigDecimal("712884.20"), new BigDecimal("712884.20"), "LOW", "ACTIVE"));

    private final AtomicLong approvalIds = new AtomicLong(100);
    private final CopyOnWriteArrayList<ApprovalView> approvals = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AuditView> audit = new CopyOnWriteArrayList<>();

    public BankingService() {
        audit.add(new AuditView(Instant.now(), "system", "APP_READY", "balance-saml",
                "SAML banking application initialized"));
    }

    public List<AccountView> accounts() {
        return accounts;
    }

    public AccountView account(long id) {
        return accounts.stream()
                .filter(account -> account.id() == id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    public ApprovalView requestApproval(long accountId, BigDecimal amount, String reason, String actor) {
        AccountView account = account(accountId);
        ApprovalView approval = new ApprovalView(
                approvalIds.incrementAndGet(),
                account.id(),
                account.accountNumber(),
                actor,
                Instant.now(),
                amount,
                reason,
                "PENDING",
                "Requested",
                "requested",
                "",
                null,
                "");
        approvals.add(approval);
        audit.add(0, new AuditView(Instant.now(), actor, "APPROVAL_REQUESTED", account.accountNumber(), reason));
        return approval;
    }

    public ApprovalView approve(long id, String note, String actor) {
        return decide(id, "APPROVED", "Approved", "approved", note, actor);
    }

    public ApprovalView reject(long id, String note, String actor) {
        return decide(id, "REJECTED", "Rejected", "rejected", note, actor);
    }

    public List<ApprovalView> pendingApprovals() {
        return approvals.stream()
                .filter(approval -> "PENDING".equals(approval.status()))
                .sorted(Comparator.comparing(ApprovalView::requestedAt).reversed())
                .toList();
    }

    public List<ApprovalView> requestHistory(String username, boolean admin) {
        return approvals.stream()
                .filter(approval -> admin || approval.requestedBy().equals(username))
                .sorted(Comparator.comparing(ApprovalView::requestedAt).reversed())
                .toList();
    }

    public List<AuditView> auditEvents() {
        return audit.stream()
                .sorted(Comparator.comparing(AuditView::createdAt).reversed())
                .limit(50)
                .toList();
    }

    public void recordBalanceCheck(long accountId, String reason, String actor) {
        AccountView account = account(accountId);
        audit.add(0, new AuditView(Instant.now(), actor, "BALANCE_VIEWED", account.accountNumber(), reason));
    }

    private ApprovalView decide(long id, String status, String displayStatus, String statusClass, String note, String actor) {
        List<ApprovalView> snapshot = new ArrayList<>(approvals);
        for (ApprovalView approval : snapshot) {
            if (approval.id() == id) {
                ApprovalView updated = new ApprovalView(
                        approval.id(),
                        approval.accountId(),
                        approval.accountNumber(),
                        approval.requestedBy(),
                        approval.requestedAt(),
                        approval.amount(),
                        approval.reason(),
                        status,
                        displayStatus,
                        statusClass,
                        actor,
                        Instant.now(),
                        note);
                approvals.remove(approval);
                approvals.add(updated);
                audit.add(0, new AuditView(Instant.now(), actor, "APPROVAL_" + status,
                        approval.accountNumber(), note));
                return updated;
            }
        }
        throw new NotFoundException("Approval not found: " + id);
    }
}
