package com.bank.customer.openbanking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open-banking/consents")
public class OpenBankingConsentController {

  private final OpenBankingConsentService service;

  public OpenBankingConsentController(OpenBankingConsentService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ConsentResponse> request(
      @Valid @RequestBody RequestConsentRequest request) {
    OpenBankingConsent consent =
        service.request(
            request.consentReference(),
            request.customerId(),
            request.tpp(),
            request.scopes(),
            request.validityDays() == 0 ? 90 : request.validityDays());
    return ResponseEntity.created(
            URI.create("/api/v1/open-banking/consents/" + consent.getConsentReference()))
        .body(ConsentResponse.from(consent));
  }

  @GetMapping("/{reference}")
  public ConsentResponse get(@PathVariable String reference) {
    return ConsentResponse.from(service.get(reference));
  }

  @PostMapping("/{reference}/authorise")
  public ConsentResponse authorise(
      @PathVariable String reference, @Valid @RequestBody AuthoriseRequest request) {
    return ConsentResponse.from(service.authorise(reference, request.scaReference()));
  }

  @PostMapping("/{reference}/revoke")
  public ConsentResponse revoke(@PathVariable String reference) {
    return ConsentResponse.from(service.revoke(reference));
  }

  public record RequestConsentRequest(
      @NotBlank @Size(max = 60) String consentReference,
      @NotNull UUID customerId,
      @NotBlank @Size(max = 120) String tpp,
      @NotEmpty Set<ConsentScope> scopes,
      int validityDays) {}

  public record AuthoriseRequest(@NotBlank @Size(max = 80) String scaReference) {}

  public record ConsentResponse(
      String consentReference,
      UUID customerId,
      String tpp,
      String scopes,
      ConsentStatus status,
      String scaReference,
      Instant expiresAt) {
    static ConsentResponse from(OpenBankingConsent c) {
      return new ConsentResponse(
          c.getConsentReference(),
          c.getCustomerId(),
          c.getTpp(),
          c.getScopes(),
          c.getStatus(),
          c.getScaReference(),
          c.getExpiresAt());
    }
  }
}
