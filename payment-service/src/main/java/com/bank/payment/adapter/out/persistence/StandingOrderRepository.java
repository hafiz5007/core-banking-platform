package com.bank.payment.adapter.out.persistence;

import com.bank.payment.domain.recurring.StandingOrder;
import com.bank.payment.domain.recurring.StandingOrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, UUID> {
  List<StandingOrder> findByStatusAndNextRunDateLessThanEqual(
      StandingOrderStatus status, LocalDate date);
}
