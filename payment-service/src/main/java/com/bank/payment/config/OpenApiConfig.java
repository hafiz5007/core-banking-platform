package com.bank.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Service API")
                .version("v1")
                .description("Canonical payment engine: intrabank transfers with saga + compensation. Sprint 4.")
                .license(new License().name("Proprietary")));
    }
}
