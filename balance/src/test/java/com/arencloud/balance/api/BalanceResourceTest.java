package com.arencloud.balance.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

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
                .body("status", org.hamcrest.Matchers.is("PENDING"));
    }

    @Test
    @TestSecurity(user = "balance.approver", roles = {"balance_user", "balance_approver"})
    void approverCanSeePendingApprovals() {
        given()
                .when().get("/api/approvals")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    @TestSecurity(user = "balance.auditor", roles = {"balance_auditor"})
    void auditorCanSeeAuditButCannotRequestApproval() {
        given()
                .when().get("/api/audit")
                .then()
                .statusCode(200);

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
    void profileExposesMappedRoles() {
        given()
                .when().get("/api/me")
                .then()
                .statusCode(200)
                .body("username", org.hamcrest.Matchers.is("balance.admin"))
                .body("roles", hasItem("balance_admin"));
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
                .body(org.hamcrest.Matchers.containsString("You have been signed out."));
    }
}
