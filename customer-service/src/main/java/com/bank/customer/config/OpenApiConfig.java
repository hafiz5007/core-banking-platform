package com.bank.customer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customerServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Customer Service API")
                .version("v1")
                .description("Customer onboarding, KYC, screening and consent. Sprint 1.")
                .license(new License().name("Proprietary")));
    }
}
