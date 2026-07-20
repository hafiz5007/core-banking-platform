package com.bank.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI adminOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Admin Service API")
                .version("v1")
                .description("RBAC, maker-checker approvals and audit trail. Sprint 10.")
                .license(new License().name("Proprietary")));
    }
}
