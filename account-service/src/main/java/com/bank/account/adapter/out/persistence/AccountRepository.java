package com.bank.account.adapter.out.persistence;

import com.bank.account.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  boolean existsByAccountNumber(String accountNumber);
}
