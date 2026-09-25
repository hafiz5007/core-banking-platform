package com.bank.account;

import com.bank.common.tenant.TenantContextFilter;
import com.bank.common.web.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@SpringBootApplication
public class AccountServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AccountServiceApplication.class, args);
  }

  /** Register the shared correlation-id filter first in the chain. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
  }

  /** Register the tenant/organization context filter just after the correlation filter. */
  @Bean
  FilterRegistrationBean<TenantContextFilter> tenantContextFilter() {
    FilterRegistrationBean<TenantContextFilter> registration =
        new FilterRegistrationBean<>(new TenantContextFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
