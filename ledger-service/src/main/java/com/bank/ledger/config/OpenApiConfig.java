package com.bank.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ledgerServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ledger Service API")
                .version("v1")
                .description("Double-entry general ledger: accounts, balanced postings, reversals, trial balance. Sprint 3.")
                .license(new License().name("Proprietary")));
    }
}
