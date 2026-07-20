package com.bank.card.adapter.out.persistence;

import com.bank.card.domain.Card;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {
}
