package com.enterpriseaudit.platform.audit;

import com.enterpriseaudit.platform.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuditWorkflowController {
    private final JdbcTemplate jdbc;

    public AuditWorkflowController(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    @GetMapping("/controls")
    public List<ControlItem> controls()
    {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT id, control_code, version, title, description, source_url,
                       effective_from, review_status, created_at
                FROM control_catalog
                WHERE tenant_id = ? AND review_status <> 'RETIRED'
                ORDER BY control_code, version DESC
                """, AuditWorkflowController::mapControl, tenantId);
    }

    @PostMapping("/controls")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'SST_REVIEWER')")
    public ControlItem createControl(@Valid @RequestBody ControlInput input)
    {
        UUID tenantId = TenantContext.requireTenantId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO control_catalog(id, tenant_id, control_code, version, title, description,
                                            source_url, effective_from)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, input.code().trim(), input.version().trim(), input.title().trim(),
                input.description().trim(), input.sourceUrl().trim(), input.effectiveFrom());
        writeAudit(tenantId, "CONTROL_VERSION_CREATED", id);
        return control(id, tenantId);
    }

    @PatchMapping("/controls/{id}/review")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'SST_REVIEWER')")
    public ControlItem reviewControl(@PathVariable UUID id, @Valid @RequestBody ControlReviewInput input,
                                     @AuthenticationPrincipal Jwt jwt)
    {
        UUID tenantId = TenantContext.requireTenantId();
        String previous = jdbc.query("SELECT review_status FROM control_catalog WHERE id = ? AND tenant_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id, tenantId);
        if (previous == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Controle não encontrado");
        }
        jdbc.update("""
                UPDATE control_catalog
                SET review_status = ?, reviewed_by = ?, reviewed_at = now()
                WHERE id = ? AND tenant_id = ?
                """, input.status().name(), jwt.getSubject(), id, tenantId);
        writeAudit(tenantId, "CONTROL_REVIEWED", id, previous, input.status().name(), input.note());
        return control(id, tenantId);
    }

    @GetMapping("/findings")
    public List<FindingItem> findings()
    {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.query("""
                SELECT f.id, f.control_id, c.control_code, f.document_id, f.title,
                       f.evidence_excerpt, f.state, f.confidence, f.review_note,
                       f.reviewed_by, f.reviewed_at, f.created_at
                FROM audit_findings f
                JOIN control_catalog c ON c.id = f.control_id AND c.tenant_id = f.tenant_id
                WHERE f.tenant_id = ?
                ORDER BY f.created_at DESC
                LIMIT 200
                """, AuditWorkflowController::mapFinding, tenantId);
    }

    @PostMapping("/findings")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'AUDIT_ANALYST', 'SST_REVIEWER')")
    public FindingItem createFinding(@Valid @RequestBody FindingInput input)
    {
        UUID tenantId = TenantContext.requireTenantId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_findings(id, tenant_id, control_id, document_id, title,
                                           evidence_excerpt, confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, input.controlId(), input.documentId(), input.title().trim(),
                input.evidenceExcerpt().trim(), input.confidence());
        writeAudit(tenantId, "FINDING_CREATED", id);
        return finding(id, tenantId);
    }

    @PatchMapping("/findings/{id}/review")
    @Transactional
    @PreAuthorize("hasAnyRole('AUDIT_ADMIN', 'SST_REVIEWER')")
    public FindingItem reviewFinding(@PathVariable UUID id, @Valid @RequestBody ReviewInput input,
                                     @AuthenticationPrincipal Jwt jwt)
    {
        UUID tenantId = TenantContext.requireTenantId();
        String previous = jdbc.query("SELECT state FROM audit_findings WHERE id = ? AND tenant_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id, tenantId);
        if (previous == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Achado não encontrado");
        }
        int updated = jdbc.update("""
                UPDATE audit_findings
                SET state = ?, review_note = ?, reviewed_by = ?, reviewed_at = now()
                WHERE id = ? AND tenant_id = ?
                """, input.state().name(), input.note().trim(), jwt.getSubject(), id, tenantId);
        if (updated == 0)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Achado não encontrado");
        }
        writeAudit(tenantId, "FINDING_REVIEWED", id, previous, input.state().name(), input.note());
        return finding(id, tenantId);
    }

    private ControlItem control(UUID id, UUID tenantId)
    {
        return jdbc.queryForObject("""
                SELECT id, control_code, version, title, description, source_url,
                       effective_from, review_status, created_at
                FROM control_catalog WHERE id = ? AND tenant_id = ?
                """, AuditWorkflowController::mapControl, id, tenantId);
    }

    private FindingItem finding(UUID id, UUID tenantId)
    {
        return jdbc.queryForObject("""
                SELECT f.id, f.control_id, c.control_code, f.document_id, f.title,
                       f.evidence_excerpt, f.state, f.confidence, f.review_note,
                       f.reviewed_by, f.reviewed_at, f.created_at
                FROM audit_findings f
                JOIN control_catalog c ON c.id = f.control_id AND c.tenant_id = f.tenant_id
                WHERE f.id = ? AND f.tenant_id = ?
                """, AuditWorkflowController::mapFinding, id, tenantId);
    }

    private void writeAudit(UUID tenantId, String action, UUID resourceId)
    {
        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, action, resourceId);
    }

    private void writeAudit(UUID tenantId, String action, UUID resourceId, String previous, String next, String note)
    {
        jdbc.update("""
                INSERT INTO audit_logs(id, tenant_id, action, resource_id, details)
                VALUES (?, ?, ?, ?, jsonb_build_object('from', ?, 'to', ?, 'note', ?))
                """, UUID.randomUUID(), tenantId, action, resourceId, previous, next, note.trim());
    }

    private static ControlItem mapControl(ResultSet rs, int row) throws SQLException
    {
        return new ControlItem(rs.getObject("id", UUID.class), rs.getString("control_code"),
                rs.getString("version"), rs.getString("title"), rs.getString("description"),
                rs.getString("source_url"), rs.getObject("effective_from", LocalDate.class),
                rs.getString("review_status"), rs.getTimestamp("created_at").toInstant());
    }

    private static FindingItem mapFinding(ResultSet rs, int row) throws SQLException
    {
        return new FindingItem(rs.getObject("id", UUID.class), rs.getObject("control_id", UUID.class),
                rs.getString("control_code"), rs.getObject("document_id", UUID.class),
                rs.getString("title"), rs.getString("evidence_excerpt"), rs.getString("state"),
                rs.getBigDecimal("confidence"), rs.getString("review_note"), rs.getString("reviewed_by"),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    public record ControlItem(UUID id, String code, String version, String title, String description,
                              String sourceUrl, LocalDate effectiveFrom, String reviewStatus, Instant createdAt) {}

    public record FindingItem(UUID id, UUID controlId, String controlCode, UUID documentId, String title,
                              String evidenceExcerpt, String state, BigDecimal confidence, String reviewNote,
                              String reviewedBy, Instant reviewedAt, Instant createdAt) {}

    public record FindingInput(@NotNull UUID controlId, UUID documentId,
                               @NotBlank @Size(max = 180) String title,
                               @NotBlank @Size(max = 5000) String evidenceExcerpt,
                               @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence) {}

    public record ReviewInput(@NotNull FindingState state, @NotBlank @Size(max = 2000) String note) {}

    public record ControlInput(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{2,80}") String code,
                               @NotBlank @Size(max = 40) String version,
                               @NotBlank @Size(max = 180) String title,
                               @NotBlank @Size(max = 5000) String description,
                               @NotBlank @Pattern(regexp = "https://.+") @Size(max = 2048) String sourceUrl,
                               LocalDate effectiveFrom) {}

    public record ControlReviewInput(@NotNull ControlReviewStatus status,
                                     @NotBlank @Size(max = 2000) String note) {}

    public enum ControlReviewStatus
    {
        DRAFT,
        REVIEWED,
        RETIRED
    }

    public enum FindingState
    {
        OPEN,
        IN_REVIEW,
        ACTION_REQUIRED,
        CONFIRMED,
        DISMISSED,
        CLOSED
    }
}
