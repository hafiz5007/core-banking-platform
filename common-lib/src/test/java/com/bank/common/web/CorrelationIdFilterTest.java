package com.bank.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesCorrelationIdWhenHeaderAbsent() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
  }

  @Test
  void reusesInboundCorrelationIdAndEchoesIt() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "corr-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-123");
  }

  @Test
  void exposesIdInMdcDuringTheChainAndClearsItAfter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "corr-abc");
    FilterChain chain =
        (req, res) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("corr-abc");

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void blankInboundHeaderIsTreatedAsAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "  ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank().isNotEqualTo("  ");
  }

  @Test
  void currentReturnsPlaceholderOutsideARequest() {
    MDC.remove(CorrelationIdFilter.MDC_KEY);

    assertThat(CorrelationIdFilter.current()).isEqualTo("n/a");
  }

  @Test
  void currentReturnsTheIdInsideARequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "corr-current");
    FilterChain chain =
        (req, res) -> assertThat(CorrelationIdFilter.current()).isEqualTo("corr-current");

    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }
}
