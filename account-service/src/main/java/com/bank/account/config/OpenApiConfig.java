package com.bank.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI accountServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Account Service API")
                .version("v1")
                .description("Open and read deposit accounts. Sprint 0 walking skeleton.")
                .license(new License().name("Proprietary")));
    }
}
