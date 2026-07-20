package com.bank.account.adapter.out.persistence;

import com.bank.account.domain.AccountHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, UUID> {
    List<AccountHolder> findByAccountId(UUID accountId);
}
