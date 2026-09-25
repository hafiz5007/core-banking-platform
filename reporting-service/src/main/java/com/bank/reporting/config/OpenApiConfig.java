package com.bank.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI reportingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Reporting Service API")
                .version("v1")
                .description(
                    "Operational & regulatory reporting from a metrics projection. Sprint 10.")
                .license(new License().name("Proprietary")));
  }
}
