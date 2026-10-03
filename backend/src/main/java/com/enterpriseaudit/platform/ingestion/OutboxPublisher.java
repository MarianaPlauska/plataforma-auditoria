package com.enterpriseaudit.platform.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, IngestionMessage> kafka;
    private final String topic;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, IngestionMessage> kafka,
                           @Value("${app.ingestion.topic}") String topic) {
        this.jdbc = jdbc; this.kafka = kafka; this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.outbox-poll-ms:1000}")
    @Transactional
    public void publishPending() {
        jdbc.queryForObject("select set_config('app.current_tenant', 'system', true)", String.class);
        List<OutboxRow> rows = jdbc.query("""
                SELECT id, tenant_id, document_id FROM ingestion_outbox
                WHERE published_at IS NULL ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new OutboxRow(rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getObject("document_id", UUID.class)));
        for (OutboxRow row : rows) {
            try {
                kafka.send(topic, row.documentId().toString(), new IngestionMessage(row.tenantId(), row.documentId())).get();
                jdbc.update("UPDATE ingestion_outbox SET published_at = now() WHERE id = ?", row.id());
            } catch (Exception failure) {
                // Leave the event pending; the next poll retries it. The consumer is idempotent.
                return;
            }
        }
    }

    private record OutboxRow(UUID id, UUID tenantId, UUID documentId) {}
}
