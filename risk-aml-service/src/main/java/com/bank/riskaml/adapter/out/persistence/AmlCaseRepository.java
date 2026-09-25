package com.bank.riskaml.adapter.out.persistence;

import com.bank.riskaml.domain.AmlCase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCaseRepository extends JpaRepository<AmlCase, UUID> {}
