package com.bank.customer.adapter.out.screening;

import com.bank.customer.application.port.ScreeningPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stub sanctions/PEP screening for local development and tests. A surname of "SANCTIONED" (case
 * insensitive) produces a positive match and an opened case reference; all others pass clean.
 *
 * <p>Replace with a real screening-engine adapter in a later iteration.
 */
@Component
public class StubScreeningAdapter implements ScreeningPort {

  @Override
  public ScreeningResult screen(ScreeningRequest request) {
    boolean match = request.lastName() != null && request.lastName().equalsIgnoreCase("SANCTIONED");
    String caseRef = match ? "case-" + UUID.randomUUID() : null;
    return new ScreeningResult(match, caseRef);
  }
}
