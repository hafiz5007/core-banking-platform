package com.bank.interestfee.adapter.out.persistence;

import com.bank.interestfee.domain.InterestPosition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestPositionRepository extends JpaRepository<InterestPosition, UUID> {
  Optional<InterestPosition> findByAccountCode(String accountCode);

  boolean existsByAccountCode(String accountCode);
}
