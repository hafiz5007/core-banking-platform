package com.bank.common.security;

import java.time.Duration;

/**
 * Settings for service-to-service authentication (ADR-008).
 *
 * <p>Deliberately a plain value type rather than {@code @ConfigurationProperties}: common-lib stays
 * framework-light, and each service binds it in its own configuration class.
 *
 * @param enabled          whether service tokens are minted and required
 * @param secret           shared signing secret (HS256); must be at least 32 bytes
 * @param serviceName      identity of this service, used as the token {@code sub} and {@code svc}
 * @param issuer           expected {@code iss}
 * @param audience         the audience this service accepts tokens for
 * @param tokenTtl         lifetime of a minted token; short by design (ADR-008 suggests ~60s)
 * @param clockSkew        tolerance for clock drift between services when validating {@code exp}
 */
public record ServiceAuthProperties(
        boolean enabled,
        String secret,
        String serviceName,
        String issuer,
        String audience,
        Duration tokenTtl,
        Duration clockSkew) {

    /** HS256 needs a key of at least 256 bits; a shorter secret is a configuration error. */
    public static final int MIN_SECRET_BYTES = 32;

    public ServiceAuthProperties {
        if (enabled) {
            if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "service-auth.secret must be at least " + MIN_SECRET_BYTES + " bytes when enabled");
            }
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalArgumentException("service-auth.service-name is required when enabled");
            }
        }
        tokenTtl = tokenTtl == null ? Duration.ofSeconds(60) : tokenTtl;
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
        issuer = issuer == null || issuer.isBlank() ? "core-banking-platform" : issuer;
    }
}
