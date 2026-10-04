package com.enterpriseaudit.platform.audit;

import com.enterpriseaudit.platform.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditHistoryController {
    private final JdbcTemplate jdbc;

    public AuditHistoryController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    @GetMapping("/events")
    public List<AuditEvent> events(@RequestParam(defaultValue = "100") int limit)
    {
        if (limit < 1 || limit > 500)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite deve estar entre 1 e 500.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT id, action, resource_id, details::text AS details, created_at
                FROM audit_logs
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, (rs, row) -> new AuditEvent(rs.getObject("id", UUID.class),
                rs.getString("action"), rs.getObject("resource_id", UUID.class),
                rs.getString("details"), rs.getTimestamp("created_at").toInstant()), tenantId, limit);
    }

    public record AuditEvent(UUID id, String action, UUID resourceId, String details, Instant createdAt) {}
}
