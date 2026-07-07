package com.arencloud.balance.ui;

import com.arencloud.balance.dto.ApprovalRequestDto;
import com.arencloud.balance.dto.DecisionRequest;
import com.arencloud.balance.service.BalanceService;
import com.arencloud.balance.service.CurrentUser;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;

@Path("/")
public class BalancePage {
    private final Template balance;
    private final BalanceService balanceService;
    private final CurrentUser currentUser;

    public BalancePage(Template balance, BalanceService balanceService, CurrentUser currentUser) {
        this.balance = balance;
        this.balanceService = balanceService;
        this.currentUser = currentUser;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({"balance_user", "balance_approver", "balance_auditor", "balance_admin"})
    public TemplateInstance index() {
        boolean canRequestApproval = currentUser.hasAny("balance_user", "balance_admin");
        boolean canAdminister = currentUser.hasAny("balance_admin");

        return balance
                .data("user", currentUser.profile())
                .data("accounts", balanceService.accounts())
                .data("approvals", currentUser.hasAny("balance_approver", "balance_admin")
                        ? balanceService.pendingApprovals()
                        : java.util.List.of())
                .data("requestHistory", canRequestApproval
                        ? (canAdminister ? balanceService.approvalRequests() : balanceService.approvalRequestsFor(currentUser.username()))
                        : java.util.List.of())
                .data("auditEvents", currentUser.hasAny("balance_auditor", "balance_admin")
                        ? balanceService.auditEvents()
                        : java.util.List.of());
    }

    @POST
    @Path("/approval-requests")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed({"balance_user", "balance_admin"})
    public Response requestApproval(@FormParam("accountId") Long accountId,
                                    @FormParam("amount") BigDecimal amount,
                                    @FormParam("reason") String reason) {
        balanceService.requestApproval(accountId, new ApprovalRequestDto(amount, reason), currentUser.username());
        return redirectHome();
    }

    @POST
    @Path("/approvals/{id}/approve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed({"balance_approver", "balance_admin"})
    public Response approve(@PathParam("id") Long id,
                            @FormParam("note") String note) {
        balanceService.approve(id, new DecisionRequest(note), currentUser.username());
        return redirectHome();
    }

    @POST
    @Path("/approvals/{id}/reject")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed({"balance_approver", "balance_admin"})
    public Response reject(@PathParam("id") Long id,
                           @FormParam("note") String note) {
        balanceService.reject(id, new DecisionRequest(note), currentUser.username());
        return redirectHome();
    }

    private Response redirectHome() {
        return Response.seeOther(URI.create("/")).build();
    }
}
