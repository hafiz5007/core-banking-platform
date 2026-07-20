package com.bank.customer.adapter.out.persistence;

import com.bank.customer.domain.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByCif(String cif);
}
