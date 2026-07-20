package com.bank.payment.adapter.out.persistence;

import com.bank.payment.domain.recurring.DirectDebitMandate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectDebitMandateRepository extends JpaRepository<DirectDebitMandate, UUID> {
    Optional<DirectDebitMandate> findByMandateReference(String mandateReference);

    boolean existsByMandateReference(String mandateReference);
}
