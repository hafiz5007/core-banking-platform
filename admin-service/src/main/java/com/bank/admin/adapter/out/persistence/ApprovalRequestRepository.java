package com.bank.admin.adapter.out.persistence;

import com.bank.admin.domain.ApprovalRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {}
