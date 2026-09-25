package com.bank.common.tenant;

/**
 * Request-scoped holder for the current organization (tenant). Populated by {@link
 * TenantContextFilter} from the {@code X-Organization-Id} header (mapped from the JWT {@code org}
 * claim at the gateway in production). Falls back to {@link #DEFAULT} when absent, so single-tenant
 * deployments work unchanged. See ADR-002.
 */
public final class TenantContext {

  /** Organization used when no tenant is supplied (single-tenant default). */
  public static final String DEFAULT = "DEFAULT";

  private static final ThreadLocal<String> ORGANIZATION = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(String organizationId) {
    ORGANIZATION.set(organizationId);
  }

  /** The current organization, or {@link #DEFAULT} if none is set. */
  public static String getOrDefault() {
    String org = ORGANIZATION.get();
    return org == null || org.isBlank() ? DEFAULT : org;
  }

  public static void clear() {
    ORGANIZATION.remove();
  }
}
