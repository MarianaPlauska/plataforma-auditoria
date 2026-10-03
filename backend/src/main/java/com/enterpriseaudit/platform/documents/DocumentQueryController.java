package com.enterpriseaudit.platform.documents;

import com.enterpriseaudit.platform.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentQueryController {
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;

    public DocumentQueryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public DocumentPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) DocumentState status) {
        UUID tenantId = TenantContext.requireTenantId();
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (long) pageNumber * pageSize;
        String statusClause = status == null ? "" : " AND status = ?";
        String sql = """
                SELECT id, original_filename, content_type, size_bytes, status, created_at, processed_at
                FROM documents WHERE tenant_id = ?
                """ + statusClause + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        List<DocumentItem> items = status == null
                ? jdbc.query(sql, DocumentQueryController::mapDocument, tenantId, pageSize, offset)
                : jdbc.query(sql, DocumentQueryController::mapDocument, tenantId, status.name(), pageSize, offset);

        String countSql = "SELECT count(*) FROM documents WHERE tenant_id = ?" + statusClause;
        long total = status == null
                ? jdbc.queryForObject(countSql, Long.class, tenantId)
                : jdbc.queryForObject(countSql, Long.class, tenantId, status.name());
        return new DocumentPage(items, pageNumber, pageSize, total);
    }

    @GetMapping("/summary")
    public DocumentMetrics summary() {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status = 'RECEIVED') AS received,
                       count(*) FILTER (WHERE status = 'PROCESSING') AS processing,
                       count(*) FILTER (WHERE status = 'READY') AS ready,
                       count(*) FILTER (WHERE status = 'FAILED') AS failed
                FROM documents WHERE tenant_id = ?
                """, (rs, row) -> new DocumentMetrics(
                rs.getLong("total"), rs.getLong("received"), rs.getLong("processing"),
                rs.getLong("ready"), rs.getLong("failed")), tenantId);
    }

    private static DocumentItem mapDocument(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new DocumentItem(
                rs.getObject("id", UUID.class),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                DocumentState.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("processed_at") == null ? null : rs.getTimestamp("processed_at").toInstant());
    }

    public record DocumentItem(UUID id, String filename, String contentType, long sizeBytes,
                               DocumentState status, Instant createdAt, Instant processedAt) {}

    public record DocumentPage(List<DocumentItem> items, int page, int size, long totalElements) {}

    public record DocumentMetrics(long total, long received, long processing, long ready, long failed) {}
}
