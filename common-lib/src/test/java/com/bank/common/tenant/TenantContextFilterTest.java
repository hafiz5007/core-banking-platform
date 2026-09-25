package com.bank.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTest {

  private final TenantContextFilter filter = new TenantContextFilter();

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void populatesTenantFromHeaderForTheDurationOfTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TenantContextFilter.HEADER, "ACME");
    FilterChain chain =
        (req, res) -> {
          assertThat(TenantContext.getOrDefault()).isEqualTo("ACME");
          assertThat(MDC.get(TenantContextFilter.MDC_KEY)).isEqualTo("ACME");
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }

  @Test
  void fallsBackToTheDefaultTenantWhenHeaderAbsent() throws Exception {
    FilterChain chain =
        (req, res) -> assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT);

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
  }

  @Test
  void blankHeaderFallsBackToTheDefaultTenant() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TenantContextFilter.HEADER, "   ");
    FilterChain chain =
        (req, res) -> assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT);

    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }

  @Test
  void clearsContextAndMdcAfterTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TenantContextFilter.HEADER, "ACME");

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

    assertThat(MDC.get(TenantContextFilter.MDC_KEY)).isNull();
    assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT);
  }

  @Test
  void contextIsUnwoundEvenWhenTheChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TenantContextFilter.HEADER, "ACME");
    FilterChain boom =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    try {
      filter.doFilter(request, new MockHttpServletResponse(), boom);
    } catch (Exception expected) {
      // The filter must still unwind its context on the way out.
    }

    assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT);
  }

  @Test
  void setAndClearRoundTrip() {
    TenantContext.set("ORG-9");
    assertThat(TenantContext.getOrDefault()).isEqualTo("ORG-9");

    TenantContext.clear();

    assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT);
  }
}
