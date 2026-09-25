package com.bank.common.security.web;

import com.bank.common.error.ErrorCode;
import com.bank.common.security.ServiceTokenVerifier;
import com.bank.common.security.ServiceTokenVerifier.InvalidServiceTokenException;
import com.bank.common.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Requires an acceptable service token on inbound REST calls (ADR-008).
 *
 * <p>Paths listed as open — health probes, metrics, API docs — are let through, because they must
 * work before any caller has a token. Everything else is rejected with 401 and the platform's
 * {@code problem+json} error shape.
 */
public class ServiceTokenAuthFilter extends HttpFilter {

  private static final Logger log = LoggerFactory.getLogger(ServiceTokenAuthFilter.class);
  private static final String BEARER = "Bearer ";

  /** Infrastructure endpoints that must answer without a token. */
  public static final List<String> DEFAULT_OPEN_PATHS =
      List.of("/actuator", "/v3/api-docs", "/swagger-ui");

  /** The verified caller service name, for handlers and logging. */
  public static final String CALLER_ATTRIBUTE = "com.bank.callerService";

  private final ServiceTokenVerifier verifier;
  private final List<String> openPaths;

  public ServiceTokenAuthFilter(ServiceTokenVerifier verifier) {
    this(verifier, DEFAULT_OPEN_PATHS);
  }

  public ServiceTokenAuthFilter(ServiceTokenVerifier verifier, List<String> openPaths) {
    this.verifier = verifier;
    this.openPaths = List.copyOf(openPaths);
  }

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (isOpen(request)) {
      chain.doFilter(request, response);
      return;
    }
    String header = request.getHeader("Authorization");
    String token =
        header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : header;
    try {
      var claims = verifier.verify(token, null);
      request.setAttribute(CALLER_ATTRIBUTE, claims.getSubject());
      chain.doFilter(request, response);
    } catch (InvalidServiceTokenException e) {
      log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
      unauthorized(response, e.getMessage());
    }
  }

  private boolean isOpen(HttpServletRequest request) {
    String path = request.getRequestURI();
    return openPaths.stream().anyMatch(path::startsWith);
  }

  /**
   * Written by hand rather than through Jackson: this filter runs before Spring's message
   * converters are in play, and the payload is a fixed shape.
   */
  private static void unauthorized(HttpServletResponse response, String reason) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            """
                {"code":"%s","message":"%s","status":401,"correlationId":"%s"}"""
                .formatted(
                    ErrorCode.UNAUTHENTICATED, escape(reason), CorrelationIdFilter.current()));
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
