package com.bank.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.common.security.ServiceTokenVerifier.InvalidServiceTokenException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Minting and verifying service-to-service tokens (ADR-008). */
class ServiceTokenTest {

  private static final String SECRET = "a-test-secret-that-is-long-enough-for-hs256";
  private static final String OTHER_SECRET = "a-different-secret-also-long-enough-for-hs256";

  private static ServiceAuthProperties props(String serviceName, String audience, String secret) {
    return new ServiceAuthProperties(
        true,
        secret,
        serviceName,
        "core-banking-platform",
        audience,
        Duration.ofSeconds(60),
        Duration.ofSeconds(30));
  }

  private static ServiceTokenIssuer accountService() {
    return new ServiceTokenIssuer(props("account-service", "account-service", SECRET));
  }

  private static ServiceTokenVerifier ledgerService() {
    return new ServiceTokenVerifier(props("ledger-service", "ledger-service", SECRET));
  }

  @Test
  void aMintedTokenIsAcceptedByItsAudience() {
    String token = accountService().mint("ledger-service", "ledger:post");

    var claims = ledgerService().verify(token, "ledger:post");

    assertThat(claims.getSubject()).isEqualTo("account-service");
    assertThat(claims.getClaim(ServiceTokenIssuer.CLAIM_SERVICE)).isEqualTo("account-service");
    assertThat(claims.getAudience()).containsExactly("ledger-service");
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
  }

  @Test
  void aTokenForAnotherServiceIsRejected() {
    String token = accountService().mint("notification-service", "ledger:post");

    assertThatThrownBy(() -> ledgerService().verify(token, "ledger:post"))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("not addressed to this service");
  }

  @Test
  void aTokenSignedWithTheWrongSecretIsRejected() {
    String token =
        new ServiceTokenIssuer(props("attacker", "attacker", OTHER_SECRET))
            .mint("ledger-service", "ledger:post");

    assertThatThrownBy(() -> ledgerService().verify(token, "ledger:post"))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("bad signature");
  }

  @Test
  void aTokenWithoutTheRequiredScopeIsRejected() {
    String token = accountService().mint("ledger-service", "ledger:read");

    assertThatThrownBy(() -> ledgerService().verify(token, "ledger:post"))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("required scope");
  }

  @Test
  void anExpiredTokenIsRejected() {
    ServiceTokenIssuer expiring =
        new ServiceTokenIssuer(
            new ServiceAuthProperties(
                true,
                SECRET,
                "account-service",
                "core-banking-platform",
                "account-service",
                Duration.ofSeconds(-120),
                Duration.ofSeconds(30)));
    String token = expiring.mint("ledger-service", "ledger:post");

    assertThatThrownBy(() -> ledgerService().verify(token, "ledger:post"))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void aTokenFromAnUnexpectedIssuerIsRejected() {
    ServiceTokenIssuer foreign =
        new ServiceTokenIssuer(
            new ServiceAuthProperties(
                true,
                SECRET,
                "account-service",
                "somewhere-else",
                "account-service",
                Duration.ofSeconds(60),
                Duration.ofSeconds(30)));

    assertThatThrownBy(
            () -> ledgerService().verify(foreign.mint("ledger-service", "ledger:post"), null))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("issuer");
  }

  @Test
  void aMissingOrMalformedTokenIsRejected() {
    ServiceTokenVerifier ledger = ledgerService();

    assertThatThrownBy(() -> ledger.verify(null, null))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("no service token");
    assertThatThrownBy(() -> ledger.verify("   ", null))
        .isInstanceOf(InvalidServiceTokenException.class);
    assertThatThrownBy(() -> ledger.verify("not-a-jwt", null))
        .isInstanceOf(InvalidServiceTokenException.class)
        .hasMessageContaining("well-formed");
  }

  @Test
  void anUnsignedTokenIsNotAccepted() {
    // "alg: none" is the classic JWT downgrade; the verifier pins HS256.
    String unsigned =
        "eyJhbGciOiJub25lIn0"
            + ".eyJzdWIiOiJhY2NvdW50LXNlcnZpY2UiLCJhdWQiOiJsZWRnZXItc2VydmljZSJ9.";

    assertThatThrownBy(() -> ledgerService().verify(unsigned, null))
        .isInstanceOf(InvalidServiceTokenException.class);
  }

  @Test
  void aShortSecretIsRefusedAtConfigurationTime() {
    assertThatThrownBy(() -> props("account-service", "account-service", "too-short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @Test
  void disabledAuthDoesNotRequireASecret() {
    ServiceAuthProperties disabled =
        new ServiceAuthProperties(false, null, null, null, null, null, null);

    assertThat(disabled.enabled()).isFalse();
    assertThat(disabled.tokenTtl()).isEqualTo(Duration.ofSeconds(60));
    assertThat(disabled.issuer()).isEqualTo("core-banking-platform");
  }
}
