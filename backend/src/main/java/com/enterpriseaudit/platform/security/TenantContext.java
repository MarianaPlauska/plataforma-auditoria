package com.enterpriseaudit.platform.security;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private TenantContext() {}

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) throw new IllegalStateException("No authenticated tenant context");
        return tenantId;
    }

    static void set(UUID tenantId) { CURRENT.set(tenantId); }
    static void clear() { CURRENT.remove(); }
}
