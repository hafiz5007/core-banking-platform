package com.bank.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI notificationOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Notification Service API")
                .version("v1")
                .description(
                    "Customer alerts, one-time passcodes and statement dispatch. Sprint 9.")
                .license(new License().name("Proprietary")));
  }
}
