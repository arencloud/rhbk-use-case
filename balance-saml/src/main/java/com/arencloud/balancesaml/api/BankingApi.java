package com.arencloud.balancesaml.api;

import com.arencloud.balancesaml.model.AccountView;
import com.arencloud.balancesaml.model.ApprovalView;
import com.arencloud.balancesaml.model.AuditView;
import com.arencloud.balancesaml.model.UserView;
import com.arencloud.balancesaml.saml.SamlPrincipal;
import com.arencloud.balancesaml.service.AccessService;
import com.arencloud.balancesaml.service.BankingService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.List;

@Path("/api")
public class BankingApi {
    private final BankingService banking;
    private final AccessService access;

    public BankingApi(BankingService banking, AccessService access) {
        this.banking = banking;
        this.access = access;
    }

    @GET
    @Path("/me")
    public UserView me(@Context HttpHeaders headers) {
        return access.user(access.require(headers,
                "balance_user", "balance_approver", "balance_auditor", "balance_admin"));
    }

    @GET
    @Path("/accounts")
    public List<AccountView> accounts(@Context HttpHeaders headers) {
        access.require(headers, "balance_user", "balance_approver", "balance_auditor", "balance_admin");
        return banking.accounts();
    }

    @GET
    @Path("/approvals")
    public List<ApprovalView> approvals(@Context HttpHeaders headers) {
        access.require(headers, "balance_approver", "balance_admin");
        return banking.pendingApprovals();
    }

    @GET
    @Path("/audit")
    public List<AuditView> audit(@Context HttpHeaders headers) {
        access.require(headers, "balance_auditor", "balance_admin");
        return banking.auditEvents();
    }

    @GET
    @Path("/requests")
    public List<ApprovalView> requests(@Context HttpHeaders headers) {
        SamlPrincipal principal = access.require(headers, "balance_user", "balance_admin");
        return banking.requestHistory(principal.nameId(), principal.hasAny("balance_admin"));
    }
}
