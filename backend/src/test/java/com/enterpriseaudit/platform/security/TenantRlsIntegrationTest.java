package com.enterpriseaudit.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.enterpriseaudit.platform.ingestion.OutboxPublisher;
import com.enterpriseaudit.platform.documents.DocumentQueryController;
import com.enterpriseaudit.platform.documents.DocumentState;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
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
}
