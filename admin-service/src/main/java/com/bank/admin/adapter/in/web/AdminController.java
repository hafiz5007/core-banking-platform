package com.bank.admin.adapter.in.web;

import com.bank.admin.application.ApprovalService;
import com.bank.admin.application.AuditService;
import com.bank.admin.application.RoleService;
import com.bank.admin.domain.ApprovalRequest;
import com.bank.admin.domain.ApprovalStatus;
import com.bank.admin.domain.AuditEvent;
import com.bank.admin.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office administration: RBAC roles, maker-checker approvals, and the audit trail. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private final RoleService roleService;
  private final ApprovalService approvalService;
  private final AuditService auditService;

  public AdminController(
      RoleService roleService, ApprovalService approvalService, AuditService auditService) {
    this.roleService = roleService;
    this.approvalService = approvalService;
    this.auditService = auditService;
  }

  // --- RBAC ---

  @PostMapping("/roles")
  public ResponseEntity<RoleResponse> createRole(
      @Valid @RequestBody CreateRoleRequest request,
      @RequestParam(defaultValue = "system") String actor) {
    Role role = roleService.create(request.name(), request.permissions(), actor);
    return ResponseEntity.status(201).body(RoleResponse.from(role));
  }

  @GetMapping("/roles")
  public List<RoleResponse> listRoles() {
    return roleService.list().stream().map(RoleResponse::from).toList();
  }

  @GetMapping("/roles/{name}/permissions/{permission}")
  public PermissionCheck check(@PathVariable String name, @PathVariable String permission) {
    return new PermissionCheck(name, permission, roleService.hasPermission(name, permission));
  }

  // --- Maker-checker ---

  @PostMapping("/approvals")
  public ResponseEntity<ApprovalResponse> submit(
      @Valid @RequestBody SubmitApprovalRequest request) {
    ApprovalRequest req =
        approvalService.submit(request.action(), request.payload(), request.maker());
    return ResponseEntity.status(201).body(ApprovalResponse.from(req));
  }

  @PostMapping("/approvals/{id}/approve")
  public ApprovalResponse approve(
      @PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
    return ApprovalResponse.from(approvalService.approve(id, request.checker()));
  }

  @PostMapping("/approvals/{id}/reject")
  public ApprovalResponse reject(
      @PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
    return ApprovalResponse.from(approvalService.reject(id, request.checker(), request.reason()));
  }

  // --- Audit ---

  @GetMapping("/audit")
  public List<AuditResponse> audit(@RequestParam String actor) {
    return auditService.byActor(actor).stream().map(AuditResponse::from).toList();
  }

  public record CreateRoleRequest(
      @NotBlank @Size(max = 60) String name, @NotEmpty Set<String> permissions) {}

  public record RoleResponse(String name, Set<String> permissions) {
    static RoleResponse from(Role r) {
      return new RoleResponse(r.getName(), r.permissionSet());
    }
  }

  public record PermissionCheck(String role, String permission, boolean granted) {}

  public record SubmitApprovalRequest(
      @NotBlank @Size(max = 80) String action,
      @NotBlank @Size(max = 2000) String payload,
      @NotBlank @Size(max = 80) String maker) {}

  public record DecisionRequest(
      @NotBlank @Size(max = 80) String checker, @Size(max = 280) String reason) {}

  public record ApprovalResponse(
      UUID id,
      String action,
      String maker,
      ApprovalStatus status,
      String checker,
      String decisionReason) {
    static ApprovalResponse from(ApprovalRequest r) {
      return new ApprovalResponse(
          r.getId(),
          r.getAction(),
          r.getMaker(),
          r.getStatus(),
          r.getChecker(),
          r.getDecisionReason());
    }
  }

  public record AuditResponse(
      String actor, String action, String target, String details, Instant occurredAt) {
    static AuditResponse from(AuditEvent e) {
      return new AuditResponse(
          e.getActor(), e.getAction(), e.getTarget(), e.getDetails(), e.getOccurredAt());
    }
  }
}
