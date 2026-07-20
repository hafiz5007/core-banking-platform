package com.bank.account.adapter.in.web;

import com.bank.account.adapter.in.web.dto.CreateProductRequest;
import com.bank.account.adapter.in.web.dto.ProductResponse;
import com.bank.account.application.ProductService;
import com.bank.account.domain.Product;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(
                request.code(), request.name(), request.accountType(),
                Currency.getInstance(request.currencyCode()), request.interestRatePercent(),
                request.monthlyFee(), request.minBalance(), request.dailyLimit(), request.overdraftLimit());
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.getCode()))
                .body(ProductResponse.from(product));
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.list().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{code}")
    public ProductResponse get(@PathVariable String code) {
        return ProductResponse.from(productService.getByCode(code));
    }
}
