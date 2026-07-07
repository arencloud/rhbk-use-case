package com.arencloud.balance.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class BalanceResourceTest {
    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void userCanReadAccountsAndRequestApproval() {
        given()
                .when().get("/api/accounts")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "amount": 1500.00,
                          "reason": "Customer requested high-value balance confirmation"
                        }
                        """)
                .when().post("/api/accounts/1/approval-requests")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.is("PENDING"))
                .body("displayStatus", org.hamcrest.Matchers.is("Requested"))
                .body("statusClass", org.hamcrest.Matchers.is("requested"));
    }

    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void userCannotApproveOrReadAudit() {
        given()
                .when().get("/api/approvals")
                .then()
                .statusCode(403);

        given()
                .when().get("/api/audit")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "balance.approver", roles = {"balance_approver"})
    void approverCanSeePendingApprovalsButCannotCreateRequests() {
        given()
                .when().get("/api/approvals")
                .then()
                .statusCode(200)
                .body(notNullValue());

        given()
                .contentType("application/json")
                .body("""
                        {
                          "amount": 1500.00,
                          "reason": "Approver should not initiate standard employee workflow"
                        }
                        """)
                .when().post("/api/accounts/1/approval-requests")
                .then()
                .statusCode(403);

        given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("accountId", "1")
                .formParam("amount", "1500.00")
                .formParam("reason", "Approver should not initiate standard employee workflow")
                .when().post("/approval-requests")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "balance.auditor", roles = {"balance_auditor"})
    void auditorCanSeeAuditButCannotRequestApproval() {
        given()
                .when().get("/api/audit")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/accounts")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        given()
                .when().get("/api/approvals")
                .then()
                .statusCode(403);

        given()
                .contentType("application/json")
                .body("""
                        {
                          "reason": "Unauthorized balance check test"
                        }
                        """)
                .when().post("/api/accounts/1/balance-checks")
                .then()
                .statusCode(403);

        given()
                .contentType("application/json")
                .body("""
                        {
                          "amount": 100.00,
                          "reason": "Unauthorized request test"
                        }
                        """)
                .when().post("/api/accounts/1/approval-requests")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "balance.admin", roles = {"balance_admin"})
    void adminHasFullApplicationAccess() {
        given()
                .when().get("/api/me")
                .then()
                .statusCode(200)
                .body("username", org.hamcrest.Matchers.is("balance.admin"))
                .body("roles", hasItem("balance_admin"));

        given()
                .when().get("/api/approvals")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/audit")
                .then()
                .statusCode(200);

        given()
                .contentType("application/json")
                .body("""
                        {
                          "amount": 2500.00,
                          "reason": "Administrative request test"
                        }
                        """)
                .when().post("/api/accounts/1/approval-requests")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.is("PENDING"));
    }

    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void callbackRedirectsAuthenticatedUserToHome() {
        given()
                .redirects().follow(false)
                .when().get("/authorization-code/callback")
                .then()
                .statusCode(303)
                .header("Location", "http://localhost:8081/");
    }

    @Test
    void loggedOutPageIsPublic() {
        given()
                .when().get("/logged-out")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("Your Balance session has ended."));
    }

    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void homePageIncludesLogoutAction() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Customer balance operations"))
                .body(containsString("href=\"/logout\""));
    }

    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void userCanCreateApprovalRequestFromUi() {
        given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("accountId", "1")
                .formParam("amount", "1500.00")
                .formParam("reason", "Customer requested high-value balance confirmation")
                .when().post("/approval-requests")
                .then()
                .statusCode(303)
                .header("Location", "http://localhost:8081/");
    }

    @Test
    @TestSecurity(user = "balance.employee", roles = {"balance_user"})
    void userHomeHidesPrivilegedPanels() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Customer Accounts"))
                .body(containsString("Approval Test"))
                .body(containsString("Request approval"))
                .body(containsString("Request Status"))
                .body(containsString("Visible requests"))
                .body(not(containsString("Pending Approvals")))
                .body(not(containsString("Audit Trail")));
    }

    @Test
    @TestSecurity(user = "balance.approver", roles = {"balance_approver"})
    void approverHomeShowsApprovalPanelOnly() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Customer Accounts"))
                .body(containsString("Pending Approvals"))
                .body(not(containsString("Approval Test")))
                .body(not(containsString("Request approval")))
                .body(not(containsString("Request Status")))
                .body(not(containsString("Audit Trail")));
    }

    @Test
    @TestSecurity(user = "balance.approver", roles = {"balance_approver"})
    void approverCanSubmitUiDecisionForExistingApproval() {
        given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("note", "Approved from Balance UI")
                .when().post("/approvals/1/approve")
                .then()
                .statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(303),
                        org.hamcrest.Matchers.is(404)));
    }

    @Test
    @TestSecurity(user = "balance.auditor", roles = {"balance_auditor"})
    void auditorHomeShowsAuditPanelOnly() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("Customer Accounts"))
                .body(not(containsString("Pending Approvals")))
                .body(not(containsString("Approval Test")))
                .body(not(containsString("Request approval")))
                .body(not(containsString("Request Status")))
                .body(containsString("Audit Trail"));
    }
}
