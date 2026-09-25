package com.bank.common.security.web;

import com.bank.common.security.ServiceTokenIssuer;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Attaches a service token to every outbound REST call (ADR-008). Register on the {@code
 * RestClient} used to call another service.
 */
public class JwtPropagationInterceptor implements ClientHttpRequestInterceptor {

  private final ServiceTokenIssuer issuer;
  private final String audience;
  private final String[] scopes;

  public JwtPropagationInterceptor(ServiceTokenIssuer issuer, String audience, String... scopes) {
    this.issuer = issuer;
    this.audience = audience;
    this.scopes = scopes.clone();
  }

  @Override
  public ClientHttpResponse intercept(
      org.springframework.http.HttpRequest request,
      byte[] body,
      ClientHttpRequestExecution execution)
      throws IOException {
    // Never overwrite a token the caller already set deliberately.
    if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
      request.getHeaders().setBearerAuth(issuer.mint(audience, scopes));
    }
    return execution.execute(request, body);
  }
}
