package com.bank.riskaml.adapter.out.persistence;

import com.bank.riskaml.domain.Alert;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {}
