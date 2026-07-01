package com.arencloud.balance.service;

import com.arencloud.balance.dto.AccountDto;
import com.arencloud.balance.dto.ApprovalDto;
import com.arencloud.balance.dto.ApprovalRequestDto;
import com.arencloud.balance.dto.AuditEventDto;
import com.arencloud.balance.dto.BalanceCheckRequest;
import com.arencloud.balance.dto.DecisionRequest;
import com.arencloud.balance.model.Account;
import com.arencloud.balance.model.ApprovalRequest;
import com.arencloud.balance.model.AuditEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class BalanceService {

    public List<AccountDto> accounts() {
        return Account.<Account>list("status", "ACTIVE")
                .stream()
                .map(AccountDto::from)
                .toList();
    }

    public AccountDto account(Long id) {
        return AccountDto.from(findAccount(id));
    }

    @Transactional
    public AccountDto checkBalance(Long accountId, BalanceCheckRequest request, String actor) {
        Account account = findAccount(accountId);
        AuditEvent.record(actor, "BALANCE_CHECK", account.accountNumber, request.reason());
        return AccountDto.from(account);
    }

    @Transactional
    public ApprovalDto requestApproval(Long accountId, ApprovalRequestDto request, String actor) {
        Account account = findAccount(accountId);

        ApprovalRequest approval = new ApprovalRequest();
        approval.accountId = account.id;
        approval.requestedBy = actor;
        approval.requestedAt = Instant.now();
        approval.amount = request.amount();
        approval.reason = request.reason();
        approval.status = "PENDING";
        approval.persist();

        AuditEvent.record(actor, "APPROVAL_REQUESTED", account.accountNumber, request.reason());
        return ApprovalDto.from(approval);
    }

    public List<ApprovalDto> pendingApprovals() {
        return ApprovalRequest.<ApprovalRequest>list("status", "PENDING")
                .stream()
                .map(ApprovalDto::from)
                .toList();
    }

    @Transactional
    public ApprovalDto approve(Long approvalId, DecisionRequest request, String actor) {
        ApprovalRequest approval = findApproval(approvalId);
        approval.status = "APPROVED";
        approval.approvedBy = actor;
        approval.approvedAt = Instant.now();

        Account account = findAccount(approval.accountId);
        AuditEvent.record(actor, "APPROVAL_APPROVED", account.accountNumber, request.note());
        return ApprovalDto.from(approval);
    }

    @Transactional
    public ApprovalDto reject(Long approvalId, DecisionRequest request, String actor) {
        ApprovalRequest approval = findApproval(approvalId);
        approval.status = "REJECTED";
        approval.approvedBy = actor;
        approval.approvedAt = Instant.now();

        Account account = findAccount(approval.accountId);
        AuditEvent.record(actor, "APPROVAL_REJECTED", account.accountNumber, request.note());
        return ApprovalDto.from(approval);
    }

    public List<AuditEventDto> auditEvents() {
        return AuditEvent.<AuditEvent>list("order by createdAt desc")
                .stream()
                .map(AuditEventDto::from)
                .toList();
    }

    private Account findAccount(Long id) {
        Account account = Account.findById(id);
        if (account == null) {
            throw new NotFoundException("Account not found: " + id);
        }
        return account;
    }

    private ApprovalRequest findApproval(Long id) {
        ApprovalRequest approval = ApprovalRequest.findById(id);
        if (approval == null) {
            throw new NotFoundException("Approval request not found: " + id);
        }
        return approval;
    }
}
