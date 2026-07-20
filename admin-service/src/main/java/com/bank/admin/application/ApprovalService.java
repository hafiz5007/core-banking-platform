package com.bank.admin.application;

import com.bank.admin.adapter.out.persistence.ApprovalRequestRepository;
import com.bank.admin.domain.ApprovalRequest;
import com.bank.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maker-checker approval workflow (FR-BO-002), with every decision written to the audit trail. */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final AuditService auditService;

    public ApprovalService(ApprovalRequestRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public ApprovalRequest submit(String action, String payload, String maker) {
        ApprovalRequest request = repository.save(new ApprovalRequest(action, payload, maker));
        auditService.record(maker, "APPROVAL_SUBMITTED", action, request.getId().toString());
        return request;
    }

    @Transactional
    public ApprovalRequest approve(UUID id, String checker) {
        ApprovalRequest request = get(id);
        request.approve(checker);
        repository.save(request);
        auditService.record(checker, "APPROVAL_APPROVED", request.getAction(), id.toString());
        return request;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, String checker, String reason) {
        ApprovalRequest request = get(id);
        request.reject(checker, reason);
        repository.save(request);
        auditService.record(checker, "APPROVAL_REJECTED", request.getAction(), id.toString());
        return request;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + id));
    }
}
