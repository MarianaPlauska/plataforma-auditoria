package com.enterpriseaudit.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.enterpriseaudit.platform.ingestion.OutboxPublisher;
import com.enterpriseaudit.platform.documents.DocumentQueryController;
import com.enterpriseaudit.platform.documents.DocumentState;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfiguration.class)
class TenantRlsIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("audit_platform").withUsername("postgres").withPassword("test-only")
            .withInitScript("init-test-db.sql");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.0.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "audit_app");
        registry.add("spring.datasource.password", () -> "audit-test-only");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired DocumentQueryController documentQueries;
    @Autowired MockMvc mockMvc;

    @Test
    void apiRequiresAValidatedJwt() throws Exception {
        mockMvc.perform(get("/api/v1/controls"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantClaimIsRequiredBeforeReadingTenantData() throws Exception {
        mockMvc.perform(get("/api/v1/controls").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void controlChangesRequireAReviewRole() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/controls")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "00000000-0000-0000-0000-000000000001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SST-TEST\",\"version\":\"1\",\"title\":\"Teste\",\"description\":\"Teste\",\"sourceUrl\":\"https://example.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void creatingTaskWritesNotificationOutboxWithoutSensitiveDetails() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/tasks")
                        .with(jwt().jwt(token -> token.claim("tenant_id", tenantId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_AUDIT_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Revisar documentação da unidade\",\"assigneeEmail\":\"responsavel@example.test\"}"))
                .andExpect(status().isOk());

        setTenant(tenantId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM remediation_tasks WHERE tenant_id = ?", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_notification_outbox WHERE tenant_id = ? AND sent_at IS NULL", Integer.class, tenantId)).isEqualTo(1);
    }

    @Test
    @Transactional
    void hrChangesCreateOneActionAndAppearInDailyInbox() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var firstImport = new MockMultipartFile("file", "vinculos.csv", "text/csv",
                "external_ref,unit_code,employment_status,effective_date\nref-123,UNIT-A,ACTIVE,2026-10-03\n".getBytes());
        var sameImport = new MockMultipartFile("file", "vinculos.csv", "text/csv",
                "external_ref,unit_code,employment_status,effective_date\nref-123,UNIT-A,ACTIVE,2026-10-03\n".getBytes());
        var changedImport = new MockMultipartFile("file", "vinculos.csv", "text/csv",
                "external_ref,unit_code,employment_status,effective_date\nref-123,UNIT-B,ACTIVE,2026-10-03\n".getBytes());
        var auth = jwt().jwt(token -> token.claim("tenant_id", tenantId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_HR_INTEGRATION"));

        mockMvc.perform(multipart("/api/v1/hr/import").file(firstImport).with(auth))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/hr/import").file(sameImport).with(auth))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/hr/import").file(changedImport).with(auth))
                .andExpect(status().isOk());

        setTenant(tenantId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hr_change_events WHERE tenant_id = ?", Integer.class, tenantId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM remediation_tasks WHERE tenant_id = ? AND category = 'HR_CHANGE'", Integer.class, tenantId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/tasks/inbox").with(jwt().jwt(token -> token.claim("tenant_id", tenantId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.items[0].category").value("HR_CHANGE"));
    }

    @Test
    @Transactional
    void requestTenantClaimFiltersFindingsThroughHttp() throws Exception {
        UUID tenantB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID controlId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        setTenant(tenantB);
        jdbc.update("INSERT INTO tenants(id, name) VALUES (?, 'Tenant B') ON CONFLICT DO NOTHING", tenantB);
        jdbc.update("""
                INSERT INTO control_catalog(id, tenant_id, control_code, version, title, description, source_url)
                VALUES (?, ?, 'HTTP-TEST', '1', 'Controle B', 'Descrição', 'https://example.test')
                """, controlId, tenantB);
        jdbc.update("""
                INSERT INTO audit_findings(id, tenant_id, control_id, title, evidence_excerpt)
                VALUES (?, ?, ?, 'Achado B', 'Trecho B')
                """, findingId, tenantB, controlId);

        mockMvc.perform(get("/api/v1/findings")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "00000000-0000-0000-0000-000000000001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + findingId + "')]").isEmpty());
    }

    @Test
    @Transactional
    void rlsHidesRowsWhenTenantContextDoesNotMatch() {
        String otherTenant = "00000000-0000-0000-0000-000000000002";
        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, otherTenant);
        jdbc.update("INSERT INTO tenants(id, name) VALUES (?::uuid, 'Other tenant')", otherTenant);
        jdbc.queryForObject("select set_config('app.current_tenant', '00000000-0000-0000-0000-000000000001', true)", String.class);
        Integer visible = jdbc.queryForObject("select count(*) from tenants", Integer.class);
        assertThat(visible).isEqualTo(1);
    }

    @Test
    @Transactional
    void outboxPublisherPublishesAndMarksTenantEvent() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID documentId = UUID.randomUUID();
        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
        jdbc.update("INSERT INTO documents(id, tenant_id, original_filename, object_key, size_bytes, status) VALUES (?, ?, 'demo.pdf', ?, 12, 'RECEIVED')",
                documentId, tenantId, tenantId + "/" + documentId + "/demo.pdf");
        UUID outboxId = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_outbox(id, tenant_id, document_id, event_type, payload) VALUES (?, ?, ?, 'DOCUMENT_INGESTION_REQUESTED', ?::jsonb)",
                outboxId, tenantId, documentId, "{\"test\":true}");
        outboxPublisher.publishPending();
        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
        Boolean published = jdbc.queryForObject("SELECT published_at IS NOT NULL FROM ingestion_outbox WHERE id = ?", Boolean.class, outboxId);
        assertThat(published).isTrue();
    }

    @Test
    @Transactional
    void documentInventoryAndMetricsAreScopedToTheCurrentTenant() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherTenant = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID readyDocument = UUID.randomUUID();
        UUID failedDocument = UUID.randomUUID();
        UUID foreignDocument = UUID.randomUUID();

        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
        jdbc.update("INSERT INTO documents(id, tenant_id, original_filename, object_key, size_bytes, status) VALUES (?, ?, 'ready.pdf', ?, 120, 'READY')",
                readyDocument, tenantId, tenantId + "/" + readyDocument + "/ready.pdf");
        jdbc.update("INSERT INTO documents(id, tenant_id, original_filename, object_key, size_bytes, status) VALUES (?, ?, 'failed.pdf', ?, 80, 'FAILED')",
                failedDocument, tenantId, tenantId + "/" + failedDocument + "/failed.pdf");

        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, otherTenant.toString());
        jdbc.update("INSERT INTO tenants(id, name) VALUES (?::uuid, 'Other tenant') ON CONFLICT (id) DO NOTHING", otherTenant.toString());
        jdbc.update("INSERT INTO documents(id, tenant_id, original_filename, object_key, size_bytes, status) VALUES (?, ?, 'foreign.pdf', ?, 100, 'READY')",
                foreignDocument, otherTenant, otherTenant + "/" + foreignDocument + "/foreign.pdf");

        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
        TenantContext.set(tenantId);
        try {
            var readyPage = documentQueries.list(0, 20, DocumentState.READY);
            var metrics = documentQueries.summary();

            assertThat(readyPage.totalElements()).isEqualTo(1);
            assertThat(readyPage.items()).extracting(DocumentQueryController.DocumentItem::id).containsExactly(readyDocument);
            assertThat(metrics.total()).isEqualTo(2);
            assertThat(metrics.ready()).isEqualTo(1);
            assertThat(metrics.failed()).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Transactional
    void auditHrTasksAndVectorsAreIsolatedBetweenTenants() {
        UUID tenantA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID tenantB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID controlId = UUID.randomUUID();
        UUID findingId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        setTenant(tenantB);
        jdbc.update("INSERT INTO tenants(id, name) VALUES (?, 'Tenant B') ON CONFLICT DO NOTHING", tenantB);
        jdbc.update("""
                INSERT INTO control_catalog(id, tenant_id, control_code, version, title, description, source_url)
                VALUES (?, ?, 'TEST-CONTROL', '1', 'Controle B', 'Descrição', 'https://example.test')
                """, controlId, tenantB);
        jdbc.update("""
                INSERT INTO audit_findings(id, tenant_id, control_id, title, evidence_excerpt)
                VALUES (?, ?, ?, 'Achado B', 'Trecho B')
                """, findingId, tenantB, controlId);
        jdbc.update("INSERT INTO remediation_tasks(id, tenant_id, finding_id, title) VALUES (?, ?, ?, 'Tarefa B')",
                taskId, tenantB, findingId);
        jdbc.update("INSERT INTO task_notification_outbox(tenant_id, task_id) VALUES (?, ?)", tenantB, taskId);
        jdbc.update("INSERT INTO hr_imports(id, tenant_id, source_name, imported_rows) VALUES (?, ?, 'rh.csv', 1)", importId, tenantB);
        jdbc.update("""
                INSERT INTO hr_people(tenant_id, external_ref, unit_code, employment_status, import_id)
                VALUES (?, 'ref-b', 'unit-b', 'ACTIVE', ?)
                """, tenantB, importId);
        jdbc.update("""
                INSERT INTO document_embeddings(id, content, metadata, embedding)
                VALUES (?, 'Texto B', ?::json, ?::vector)
                """, UUID.randomUUID(), "{\"tenant_id\":\"" + tenantB + "\"}", zeroVector());

        setTenant(tenantA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM control_catalog WHERE control_code = 'TEST-CONTROL'", Integer.class)).isZero();
        assertThat(count("audit_findings")).isZero();
        assertThat(count("remediation_tasks")).isZero();
        assertThat(count("task_notification_outbox")).isZero();
        assertThat(count("hr_imports")).isZero();
        assertThat(count("hr_people")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_embeddings", Integer.class)).isZero();
    }

    private void setTenant(UUID tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String zeroVector() {
        return "[" + "0,".repeat(767) + "0]";
    }
}
