package com.bank.card.adapter.out.persistence;

import com.bank.card.domain.Authorization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {}
