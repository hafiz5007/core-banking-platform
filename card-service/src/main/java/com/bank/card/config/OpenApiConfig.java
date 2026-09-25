package com.bank.card.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI cardServiceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Card Service API")
                .version("v1")
                .description("Debit card issuance and real-time authorization. Sprint 8.")
                .license(new License().name("Proprietary")));
  }
}
