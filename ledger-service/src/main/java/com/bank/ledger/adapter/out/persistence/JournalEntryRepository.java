package com.bank.ledger.adapter.out.persistence;

import com.bank.ledger.domain.Direction;
import com.bank.ledger.domain.JournalEntry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);

    /** Sum of all posted line amounts on a given side — used to prove the trial balance. */
    @Query("select coalesce(sum(l.amount), 0) from JournalLine l where l.direction = :direction")
    BigDecimal sumByDirection(@Param("direction") Direction direction);
}
