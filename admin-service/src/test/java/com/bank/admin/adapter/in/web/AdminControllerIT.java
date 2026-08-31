package com.bank.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.admin.AbstractIntegrationTest;
import com.bank.admin.adapter.in.web.AdminController.ApprovalResponse;
import com.bank.admin.adapter.in.web.AdminController.AuditResponse;
import com.bank.admin.adapter.in.web.AdminController.CreateRoleRequest;
import com.bank.admin.adapter.in.web.AdminController.DecisionRequest;
import com.bank.admin.adapter.in.web.AdminController.PermissionCheck;
import com.bank.admin.adapter.in.web.AdminController.RoleResponse;
import com.bank.admin.adapter.in.web.AdminController.SubmitApprovalRequest;
import com.bank.admin.domain.ApprovalStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end tests of RBAC, the maker-checker workflow and the audit trail (FR-BO-002). */
class AdminControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createsARoleAndAnswersPermissionChecks() {
        String role = unique("TELLER");

        ResponseEntity<RoleResponse> created = rest.postForEntity("/api/v1/admin/roles",
                new CreateRoleRequest(role, Set.of("account:read", "account:open")), RoleResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().permissions()).contains("account:read");

        ResponseEntity<PermissionCheck> granted = rest.getForEntity(
                "/api/v1/admin/roles/" + role + "/permissions/account:read", PermissionCheck.class);
        assertThat(granted.getBody().granted()).isTrue();

        ResponseEntity<PermissionCheck> denied = rest.getForEntity(
                "/api/v1/admin/roles/" + role + "/permissions/payment:release", PermissionCheck.class);
        assertThat(denied.getBody().granted()).isFalse();
    }

    @Test
    void submitsAndApprovesUnderFourEyes() {
        String maker = unique("maker");
        ResponseEntity<ApprovalResponse> submitted = rest.postForEntity("/api/v1/admin/approvals",
                new SubmitApprovalRequest("LIMIT_INCREASE", "{\"accountCode\":\"1000\"}", maker),
                ApprovalResponse.class);

        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitted.getBody().status()).isEqualTo(ApprovalStatus.PENDING);

        UUID id = submitted.getBody().id();
        ResponseEntity<ApprovalResponse> approved = rest.postForEntity(
                "/api/v1/admin/approvals/" + id + "/approve",
                new DecisionRequest(unique("checker"), null), ApprovalResponse.class);

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void rejectsAnApprovalWithAReason() {
        String maker = unique("maker");
        UUID id = rest.postForEntity("/api/v1/admin/approvals",
                new SubmitApprovalRequest("FEE_WAIVER", "{}", maker), ApprovalResponse.class)
                .getBody().id();

        ResponseEntity<ApprovalResponse> rejected = rest.postForEntity(
                "/api/v1/admin/approvals/" + id + "/reject",
                new DecisionRequest(unique("checker"), "insufficient justification"), ApprovalResponse.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().status()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void makerCannotBeTheirOwnChecker() {
        String maker = unique("maker");
        UUID id = rest.postForEntity("/api/v1/admin/approvals",
                new SubmitApprovalRequest("LIMIT_INCREASE", "{}", maker), ApprovalResponse.class)
                .getBody().id();

        ResponseEntity<String> response = rest.postForEntity("/api/v1/admin/approvals/" + id + "/approve",
                new DecisionRequest(maker, null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("BUSINESS_RULE_VIOLATION");
    }

    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() {
        String maker = unique("maker");
        UUID id = rest.postForEntity("/api/v1/admin/approvals",
                new SubmitApprovalRequest("LIMIT_INCREASE", "{}", maker), ApprovalResponse.class)
                .getBody().id();
        rest.postForEntity("/api/v1/admin/approvals/" + id + "/approve",
                new DecisionRequest(unique("checker"), null), ApprovalResponse.class);

        ResponseEntity<String> second = rest.postForEntity("/api/v1/admin/approvals/" + id + "/approve",
                new DecisionRequest(unique("checker"), null), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void everyDecisionIsWrittenToTheAuditTrail() {
        String maker = unique("maker");
        String checker = unique("checker");
        UUID id = rest.postForEntity("/api/v1/admin/approvals",
                new SubmitApprovalRequest("LIMIT_INCREASE", "{}", maker), ApprovalResponse.class)
                .getBody().id();
        rest.postForEntity("/api/v1/admin/approvals/" + id + "/approve",
                new DecisionRequest(checker, null), ApprovalResponse.class);

        ResponseEntity<java.util.List<AuditResponse>> makerTrail = rest.exchange(
                "/api/v1/admin/audit?actor=" + maker, HttpMethod.GET, null,
                new ParameterizedTypeReference<java.util.List<AuditResponse>>() { });
        ResponseEntity<java.util.List<AuditResponse>> checkerTrail = rest.exchange(
                "/api/v1/admin/audit?actor=" + checker, HttpMethod.GET, null,
                new ParameterizedTypeReference<java.util.List<AuditResponse>>() { });

        assertThat(makerTrail.getBody()).extracting(AuditResponse::action).contains("APPROVAL_SUBMITTED");
        assertThat(checkerTrail.getBody()).extracting(AuditResponse::action).contains("APPROVAL_APPROVED");
    }

    @Test
    void unknownApprovalIsNotFound() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/admin/approvals/" + UUID.randomUUID() + "/approve",
                new DecisionRequest("checker", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
