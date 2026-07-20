package com.bank.admin.application;

import com.bank.admin.adapter.out.persistence.RoleRepository;
import com.bank.admin.domain.Role;
import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RBAC role catalogue (FR-BO-001). */
@Service
public class RoleService {

    private final RoleRepository repository;
    private final AuditService auditService;

    public RoleService(RoleRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public Role create(String name, Set<String> permissions, String actor) {
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Role already exists: " + name);
        }
        Role role = repository.save(new Role(name, permissions));
        auditService.record(actor, "ROLE_CREATED", name, String.join(",", permissions));
        return role;
    }

    @Transactional(readOnly = true)
    public Role getByName(String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name));
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(String roleName, String permission) {
        return getByName(roleName).hasPermission(permission);
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        return repository.findAll();
    }
}
