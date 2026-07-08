package com.arencloud.balancesaml.ui;

import com.arencloud.balancesaml.saml.SamlPrincipal;
import com.arencloud.balancesaml.service.AccessService;
import com.arencloud.balancesaml.service.BankingService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.net.URI;

@Path("/")
public class BankingPage {
    private final Template banking;
    private final Template loggedOut;
    private final BankingService bankingService;
    private final AccessService access;

    public BankingPage(Template banking, Template loggedOut, BankingService bankingService, AccessService access) {
        this.banking = banking;
        this.loggedOut = loggedOut;
        this.bankingService = bankingService;
        this.access = access;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@Context HttpHeaders headers) {
        return access.current(headers)
                .map(principal -> Response.ok(page(principal)).build())
                .orElseGet(() -> access.redirectToLogin("/"));
    }

    @GET
    @Path("/logged-out")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance loggedOut() {
        return loggedOut.instance();
    }

    @POST
    @Path("/balance-checks")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response balanceCheck(@FormParam("accountId") long accountId,
                                 @FormParam("reason") String reason,
                                 @Context HttpHeaders headers) {
        SamlPrincipal principal = access.require(headers, "balance_user", "balance_admin");
        bankingService.recordBalanceCheck(accountId, reason, principal.nameId());
        return home();
    }

    @POST
    @Path("/approval-requests")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approvalRequest(@FormParam("accountId") long accountId,
                                    @FormParam("amount") BigDecimal amount,
                                    @FormParam("reason") String reason,
                                    @Context HttpHeaders headers) {
        SamlPrincipal principal = access.require(headers, "balance_user", "balance_admin");
        bankingService.requestApproval(accountId, amount, reason, principal.nameId());
        return home();
    }

    @POST
    @Path("/approvals/{id}/approve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approve(@PathParam("id") long id,
                            @FormParam("note") String note,
                            @Context HttpHeaders headers) {
        SamlPrincipal principal = access.require(headers, "balance_approver", "balance_admin");
        bankingService.approve(id, note, principal.nameId());
        return home();
    }

    @POST
    @Path("/approvals/{id}/reject")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response reject(@PathParam("id") long id,
                           @FormParam("note") String note,
                           @Context HttpHeaders headers) {
        SamlPrincipal principal = access.require(headers, "balance_approver", "balance_admin");
        bankingService.reject(id, note, principal.nameId());
        return home();
    }

    private TemplateInstance page(SamlPrincipal principal) {
        boolean canRequestApproval = principal.hasAny("balance_user", "balance_admin");
        boolean canAdminister = principal.hasAny("balance_admin");
        return banking
                .data("user", access.user(principal))
                .data("accounts", bankingService.accounts())
                .data("approvals", principal.hasAny("balance_approver", "balance_admin")
                        ? bankingService.pendingApprovals()
                        : java.util.List.of())
                .data("requestHistory", canRequestApproval
                        ? bankingService.requestHistory(principal.nameId(), canAdminister)
                        : java.util.List.of())
                .data("auditEvents", principal.hasAny("balance_auditor", "balance_admin")
                        ? bankingService.auditEvents()
                        : java.util.List.of());
    }

    private static Response home() {
        return Response.seeOther(URI.create("/")).build();
    }
}
