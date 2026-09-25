package com.bank.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;

/**
 * Populates {@link TenantContext} for the duration of a request from the {@code X-Organization-Id}
 * header, and mirrors it into the SLF4J MDC so it appears on every log line. Register early in each
 * service's filter chain (after the correlation filter). See ADR-002.
 */
public class TenantContextFilter extends HttpFilter {

  public static final String HEADER = "X-Organization-Id";
  public static final String MDC_KEY = "organizationId";

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String org = request.getHeader(HEADER);
    if (org == null || org.isBlank()) {
      org = TenantContext.DEFAULT;
    }
    TenantContext.set(org);
    MDC.put(MDC_KEY, org);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      TenantContext.clear();
    }
  }
}
