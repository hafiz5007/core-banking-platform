package com.bank.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints short-lived tokens identifying this service to another (ADR-008).
 *
 * <p>Claims follow the ADR: {@code sub} and {@code svc} are the calling service, {@code aud} the
 * target, {@code scope} what the caller is asking to do, and {@code jti} makes an individual token
 * identifiable for revocation or replay investigation. The lifetime is deliberately short, which is
 * what limits the blast radius of a leaked token.
 */
public class ServiceTokenIssuer {

    public static final String CLAIM_SERVICE = "svc";
    public static final String CLAIM_SCOPE = "scope";

    private final ServiceAuthProperties properties;
    private final MACSigner signer;

    public ServiceTokenIssuer(ServiceAuthProperties properties) {
        this.properties = properties;
        try {
            this.signer = new MACSigner(properties.secret().getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to build the service-token signer", e);
        }
    }

    /**
     * Mint a token for one call.
     *
     * @param audience the service being called
     * @param scopes   the operations being requested (e.g. {@code ledger:post})
     */
    public String mint(String audience, String... scopes) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(properties.serviceName())
                .issuer(properties.issuer())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.tokenTtl())))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_SERVICE, properties.serviceName())
                .claim(CLAIM_SCOPE, String.join(" ", List.of(scopes)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign the service token", e);
        }
        return jwt.serialize();
    }
}
