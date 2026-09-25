package com.bank.customer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base class providing a real PostgreSQL via Testcontainers for full-stack integration tests. */
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
  static void disableTracingExport(DynamicPropertyRegistry registry) {
    registry.add("management.tracing.enabled", () -> "false");
  }
}
