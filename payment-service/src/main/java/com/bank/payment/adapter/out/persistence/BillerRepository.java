package com.bank.payment.adapter.out.persistence;

import com.bank.payment.domain.directory.Biller;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillerRepository extends JpaRepository<Biller, UUID> {
  Optional<Biller> findByBillerCode(String billerCode);

  boolean existsByBillerCode(String billerCode);
}
