package com.bank.ledger.adapter.out.persistence;

import com.bank.ledger.domain.LedgerAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
  Optional<LedgerAccount> findByCode(String code);

  boolean existsByCode(String code);
}
