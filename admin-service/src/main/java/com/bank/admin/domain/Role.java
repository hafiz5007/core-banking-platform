package com.bank.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An RBAC role: a named set of permissions (FR-BO-001). Permissions are stored comma-separated and
 * exposed as a set; least-privilege is achieved by assigning narrowly scoped roles.
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, length = 60)
    private String organizationId;

    @Column(nullable = false, unique = true, updatable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 1000)
    private String permissions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {
        // Required by JPA.
    }

    public Role(String name, Set<String> permissions) {
        this.id = UUID.randomUUID();
        this.organizationId = com.bank.common.tenant.TenantContext.getOrDefault();
        this.name = name;
        this.permissions = String.join(",", permissions);
        this.createdAt = Instant.now();
    }

    public boolean hasPermission(String permission) {
        return permissionSet().contains(permission);
    }

    public Set<String> permissionSet() {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(",")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPermissions() {
        return permissions;
    }
}
