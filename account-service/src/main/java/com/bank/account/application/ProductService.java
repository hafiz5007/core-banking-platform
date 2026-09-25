package com.bank.account.application;

import com.bank.account.adapter.out.persistence.ProductRepository;
import com.bank.account.domain.AccountType;
import com.bank.account.domain.Product;
import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.common.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product factory: lets authorized staff define and list products (FR-ACC-003). */
@Service
public class ProductService {

  private final ProductRepository repository;

  public ProductService(ProductRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Product create(
      String code,
      String name,
      AccountType accountType,
      Currency currency,
      BigDecimal interestRatePercent,
      BigDecimal monthlyFee,
      BigDecimal minBalance,
      BigDecimal dailyLimit,
      BigDecimal overdraftLimit) {
    if (repository.existsByCode(code)) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_REQUEST, "Product code already exists: " + code);
    }
    return repository.save(
        new Product(
            code,
            name,
            accountType,
            currency,
            interestRatePercent,
            monthlyFee,
            minBalance,
            dailyLimit,
            overdraftLimit));
  }

  @Transactional(readOnly = true)
  public Product getByCode(String code) {
    return repository
        .findByCode(code)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + code));
  }

  @Transactional(readOnly = true)
  public List<Product> list() {
    return repository.findAll();
  }
}
