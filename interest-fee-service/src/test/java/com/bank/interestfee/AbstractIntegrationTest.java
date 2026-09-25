package com.bank.interestfee;

import com.bank.interestfee.application.port.LedgerPort;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class with a real PostgreSQL and an in-memory ledger so accrual/fee flows run end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    // Started once per JVM and deliberately never stopped: Spring caches the application
    // context across test classes, so a container tied to one class's lifecycle gets torn
    // down while a later class still points its DataSource at it. Ryuk reaps it at exit.
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("management.tracing.enabled", () -> "false");
  }

  @TestConfiguration
  public static class FakeLedgerConfig {
    @Bean
    @Primary
    public LedgerPort fakeLedgerPort() {
      return command -> UUID.randomUUID();
    }
  }
}
