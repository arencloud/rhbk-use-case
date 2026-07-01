package com.arencloud.balance.api;

import com.arencloud.balance.dto.AccountDto;
import com.arencloud.balance.dto.ApprovalDto;
import com.arencloud.balance.dto.ApprovalRequestDto;
import com.arencloud.balance.dto.AuditEventDto;
import com.arencloud.balance.dto.BalanceCheckRequest;
import com.arencloud.balance.dto.DecisionRequest;
import com.arencloud.balance.dto.UserProfileDto;
import com.arencloud.balance.service.BalanceService;
import com.arencloud.balance.service.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.util.List;

@Path("/api")
public class BalanceResource {
    private static final String[] ANY_BALANCE_ROLE = {
            "balance_user", "balance_approver", "balance_auditor", "balance_admin"
    };

    private final BalanceService balanceService;
    private final CurrentUser currentUser;

    public BalanceResource(BalanceService balanceService, CurrentUser currentUser) {
        this.balanceService = balanceService;
        this.currentUser = currentUser;
    }

    @GET
    @Path("/me")
    @RolesAllowed({"balance_user", "balance_approver", "balance_auditor", "balance_admin"})
    public UserProfileDto me() {
        return currentUser.profile();
    }

    @GET
    @Path("/accounts")
    @RolesAllowed({"balance_user", "balance_approver", "balance_auditor", "balance_admin"})
    public List<AccountDto> accounts() {
        return balanceService.accounts();
    }

    @GET
    @Path("/accounts/{id}")
    @RolesAllowed({"balance_user", "balance_approver", "balance_auditor", "balance_admin"})
    public AccountDto account(@PathParam("id") Long id) {
        return balanceService.account(id);
    }

    @POST
    @Path("/accounts/{id}/balance-checks")
    @RolesAllowed({"balance_user", "balance_approver", "balance_admin"})
    public AccountDto checkBalance(@PathParam("id") Long id, @Valid BalanceCheckRequest request) {
        return balanceService.checkBalance(id, request, currentUser.username());
    }

    @POST
    @Path("/accounts/{id}/approval-requests")
    @RolesAllowed({"balance_user", "balance_approver", "balance_admin"})
    public ApprovalDto requestApproval(@PathParam("id") Long id, @Valid ApprovalRequestDto request) {
        return balanceService.requestApproval(id, request, currentUser.username());
    }

    @GET
    @Path("/approvals")
    @RolesAllowed({"balance_approver", "balance_admin"})
    public List<ApprovalDto> approvals() {
        return balanceService.pendingApprovals();
    }

    @POST
    @Path("/approvals/{id}/approve")
    @RolesAllowed({"balance_approver", "balance_admin"})
    public ApprovalDto approve(@PathParam("id") Long id, @Valid DecisionRequest request) {
        return balanceService.approve(id, request, currentUser.username());
    }

    @POST
    @Path("/approvals/{id}/reject")
    @RolesAllowed({"balance_approver", "balance_admin"})
    public ApprovalDto reject(@PathParam("id") Long id, @Valid DecisionRequest request) {
        return balanceService.reject(id, request, currentUser.username());
    }

    @GET
    @Path("/audit")
    @RolesAllowed({"balance_auditor", "balance_admin"})
    public List<AuditEventDto> audit() {
        return balanceService.auditEvents();
    }
}
