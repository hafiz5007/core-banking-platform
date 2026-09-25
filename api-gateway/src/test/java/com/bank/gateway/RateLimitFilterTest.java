package com.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for the fixed-window rate limiter (FR-CHN-004). */
class RateLimitFilterTest {

  private static MockHttpServletRequest request(String uri, String client) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    request.setRemoteAddr(client);
    return request;
  }

  @Test
  void allowsRequestsUpToTheLimit() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(3);
    AtomicInteger passed = new AtomicInteger();
    FilterChain chain = (req, res) -> passed.incrementAndGet();

    for (int i = 0; i < 3; i++) {
      filter.doFilter(
          request("/api/v1/accounts", "10.0.0.1"), new MockHttpServletResponse(), chain);
    }

    assertThat(passed.get()).isEqualTo(3);
  }

  @Test
  void rejectsWithTooManyRequestsOnceTheLimitIsExceeded() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(2);
    FilterChain chain = (req, res) -> {};
    for (int i = 0; i < 2; i++) {
      filter.doFilter(
          request("/api/v1/accounts", "10.0.0.2"), new MockHttpServletResponse(), chain);
    }

    MockHttpServletResponse rejected = new MockHttpServletResponse();
    filter.doFilter(request("/api/v1/accounts", "10.0.0.2"), rejected, chain);

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void theChainIsNotInvokedForARejectedRequest() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1);
    AtomicInteger passed = new AtomicInteger();
    FilterChain chain = (req, res) -> passed.incrementAndGet();

    filter.doFilter(request("/api/v1/payments", "10.0.0.3"), new MockHttpServletResponse(), chain);
    filter.doFilter(request("/api/v1/payments", "10.0.0.3"), new MockHttpServletResponse(), chain);

    assertThat(passed.get()).isEqualTo(1);
  }

  @Test
  void limitsAreTrackedPerClient() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1);
    FilterChain chain = (req, res) -> {};
    filter.doFilter(request("/api/v1/accounts", "10.0.0.4"), new MockHttpServletResponse(), chain);

    MockHttpServletResponse otherClient = new MockHttpServletResponse();
    filter.doFilter(request("/api/v1/accounts", "10.0.0.5"), otherClient, chain);

    assertThat(otherClient.getStatus()).isEqualTo(200);
  }

  @Test
  void forwardedForIdentifiesTheClientAheadOfTheRemoteAddress() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1);
    FilterChain chain = (req, res) -> {};

    MockHttpServletRequest first = request("/api/v1/accounts", "10.0.0.6");
    first.addHeader("X-Forwarded-For", "203.0.113.9");
    filter.doFilter(first, new MockHttpServletResponse(), chain);

    // Same proxied client, different remote address: still the same window.
    MockHttpServletRequest second = request("/api/v1/accounts", "10.0.0.7");
    second.addHeader("X-Forwarded-For", "203.0.113.9");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(second, response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
  }

  @Test
  void actuatorProbesAreNeverRateLimited() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1);
    AtomicInteger passed = new AtomicInteger();
    FilterChain chain = (req, res) -> passed.incrementAndGet();

    for (int i = 0; i < 5; i++) {
      filter.doFilter(
          request("/actuator/health", "10.0.0.8"), new MockHttpServletResponse(), chain);
    }

    assertThat(passed.get()).isEqualTo(5);
  }
}
