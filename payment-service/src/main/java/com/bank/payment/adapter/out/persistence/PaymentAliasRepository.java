package com.bank.payment.adapter.out.persistence;

import com.bank.payment.domain.directory.PaymentAlias;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAliasRepository extends JpaRepository<PaymentAlias, UUID> {
  Optional<PaymentAlias> findByAlias(String alias);

  boolean existsByAlias(String alias);
}
