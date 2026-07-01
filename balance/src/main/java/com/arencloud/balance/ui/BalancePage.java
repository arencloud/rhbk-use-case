package com.arencloud.balance.ui;

import com.arencloud.balance.service.BalanceService;
import com.arencloud.balance.service.CurrentUser;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
        return balance
                .data("user", currentUser.profile())
                .data("accounts", balanceService.accounts())
                .data("approvals", currentUser.hasAny("balance_approver", "balance_admin")
                        ? balanceService.pendingApprovals()
                        : java.util.List.of())
                .data("auditEvents", currentUser.hasAny("balance_auditor", "balance_admin")
                        ? balanceService.auditEvents()
                        : java.util.List.of());
    }
}
