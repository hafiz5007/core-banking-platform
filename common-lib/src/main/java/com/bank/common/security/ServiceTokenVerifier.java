package com.bank.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Verifies a service token (ADR-008): signature, algorithm, issuer, audience, expiry, and the scope
 * the operation requires.
 *
 * <p>Every failure raises {@link InvalidServiceTokenException} with a short reason. The reason is
 * for logs and for the caller's status code — it deliberately carries no token contents.
 */
public class ServiceTokenVerifier {

  private final ServiceAuthProperties properties;
  private final MACVerifier verifier;

  public ServiceTokenVerifier(ServiceAuthProperties properties) {
    this.properties = properties;
    try {
      this.verifier = new MACVerifier(properties.secret().getBytes(StandardCharsets.UTF_8));
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to build the service-token verifier", e);
    }
  }

  /**
   * Verify a token and the scope it must carry.
   *
   * @param token the raw compact JWT (no {@code Bearer } prefix)
   * @param requiredScope scope the operation demands, or {@code null} to only authenticate
   * @return the verified claims
   * @throws InvalidServiceTokenException if the token is missing, malformed, or not acceptable
   */
  public JWTClaimsSet verify(String token, String requiredScope) {
    if (token == null || token.isBlank()) {
      throw new InvalidServiceTokenException("no service token presented");
    }
    SignedJWT jwt;
    try {
      jwt = SignedJWT.parse(token);
    } catch (ParseException e) {
      throw new InvalidServiceTokenException("token is not a well-formed JWT");
    }

    // Pin the algorithm: never let the token's own header talk us into a weaker one.
    if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
      throw new InvalidServiceTokenException("unexpected signing algorithm");
    }
    try {
      if (!jwt.verify(verifier)) {
        throw new InvalidServiceTokenException("bad signature");
      }
    } catch (JOSEException e) {
      throw new InvalidServiceTokenException("signature could not be checked");
    }

    JWTClaimsSet claims;
    try {
      claims = jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw new InvalidServiceTokenException("token claims are not readable");
    }

    if (!properties.issuer().equals(claims.getIssuer())) {
      throw new InvalidServiceTokenException("unexpected issuer");
    }
    List<String> audience = claims.getAudience();
    if (audience == null || !audience.contains(properties.audience())) {
      throw new InvalidServiceTokenException("token is not addressed to this service");
    }
    Date expiry = claims.getExpirationTime();
    if (expiry == null) {
      throw new InvalidServiceTokenException("token has no expiry");
    }
    if (Instant.now().minus(properties.clockSkew()).isAfter(expiry.toInstant())) {
      throw new InvalidServiceTokenException("token has expired");
    }
    if (requiredScope != null && !scopesOf(claims).contains(requiredScope)) {
      throw new InvalidServiceTokenException("token lacks the required scope");
    }
    return claims;
  }

  private static Set<String> scopesOf(JWTClaimsSet claims) {
    Object raw = claims.getClaim(ServiceTokenIssuer.CLAIM_SCOPE);
    if (raw == null) {
      return Set.of();
    }
    return Set.copyOf(Arrays.asList(raw.toString().trim().split("\\s+")));
  }

  /** Raised when a presented service token cannot be accepted. */
  public static class InvalidServiceTokenException extends RuntimeException {
    public InvalidServiceTokenException(String reason) {
      super(reason);
    }
  }
}
