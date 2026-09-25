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

  /**
   * Load an entry with its lines in one query. Needed because { open-in-view} is off: the lazy {
   * lines} collection would otherwise be mapped to a response after the transaction has closed.
   * Also avoids the N+1 the plain finder would cause.
   */
  @Query("select distinct e from JournalEntry e left join fetch e.lines where e.id = :id")
  Optional<JournalEntry> findByIdWithLines(@Param("id") UUID id);

  /** Sum of all posted line amounts on a given side — used to prove the trial balance. */
  @Query("select coalesce(sum(l.amount), 0) from JournalLine l where l.direction = :direction")
  BigDecimal sumByDirection(@Param("direction") Direction direction);
}
