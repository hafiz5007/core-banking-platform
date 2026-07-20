package com.bank.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Smoke test: the gateway context loads with routes, security (dev mode) and the rate limiter. */
@SpringBootTest
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the gateway application context cannot start.
    }
}
