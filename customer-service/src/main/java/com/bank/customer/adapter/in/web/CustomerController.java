package com.bank.customer.adapter.in.web;

import com.bank.customer.adapter.in.web.dto.ChangeLogResponse;
import com.bank.customer.adapter.in.web.dto.ConsentResponse;
import com.bank.customer.adapter.in.web.dto.CustomerResponse;
import com.bank.customer.adapter.in.web.dto.OnboardCustomerRequest;
import com.bank.customer.adapter.in.web.dto.RecordConsentRequest;
import com.bank.customer.application.ChangeLogRecorder;
import com.bank.customer.application.CustomerService;
import com.bank.customer.application.CustomerService.OnboardCommand;
import com.bank.customer.domain.Consent;
import com.bank.customer.domain.Customer;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService customerService;
  private final ChangeLogRecorder changeLog;

  public CustomerController(CustomerService customerService, ChangeLogRecorder changeLog) {
    this.customerService = customerService;
    this.changeLog = changeLog;
  }

  @PostMapping
  public ResponseEntity<CustomerResponse> onboard(
      @Valid @RequestBody OnboardCustomerRequest request) {
    Customer customer =
        customerService.onboard(
            new OnboardCommand(
                request.firstName(),
                request.lastName(),
                request.dateOfBirth(),
                request.nationality(),
                request.email(),
                request.phone(),
                request.taxId(),
                request.dataProcessingConsent(),
                request.channel()));
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId()))
        .body(CustomerResponse.from(customer));
  }

  @GetMapping("/{id}")
  public CustomerResponse get(@PathVariable UUID id) {
    return CustomerResponse.from(customerService.get(id));
  }

  @GetMapping("/{id}/change-log")
  public List<ChangeLogResponse> changeLog(@PathVariable UUID id) {
    customerService.get(id); // 404 if unknown
    return changeLog.history("Customer", id.toString()).stream()
        .map(ChangeLogResponse::from)
        .toList();
  }

  @GetMapping("/{id}/consents")
  public List<ConsentResponse> consents(@PathVariable UUID id) {
    return customerService.consentsFor(id).stream().map(ConsentResponse::from).toList();
  }

  @PostMapping("/{id}/consents")
  public ResponseEntity<ConsentResponse> recordConsent(
      @PathVariable UUID id, @Valid @RequestBody RecordConsentRequest request) {
    Consent consent =
        customerService.recordConsent(
            id, request.consentType(), request.granted(), request.channel());
    return ResponseEntity.status(201).body(ConsentResponse.from(consent));
  }
}
