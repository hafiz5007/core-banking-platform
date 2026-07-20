package com.bank.interestfee.adapter.out.persistence;

import com.bank.interestfee.domain.FeeCharge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeChargeRepository extends JpaRepository<FeeCharge, UUID> {
}
