package com.bank.account.adapter.out.persistence;

import com.bank.account.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);
}
