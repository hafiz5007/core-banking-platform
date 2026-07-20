package com.bank.interestfee.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI interestFeeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Interest & Fee Service API")
                .version("v1")
                .description("Interest accrual & capitalization, fees, end-of-day batch. Sprint 7.")
                .license(new License().name("Proprietary")));
    }
}
