package com.bank.riskaml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI riskAmlOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Risk & AML Service API")
                .version("v1")
                .description("Transaction monitoring, alerts, case management and SAR. Sprint 10.")
                .license(new License().name("Proprietary")));
  }
}
