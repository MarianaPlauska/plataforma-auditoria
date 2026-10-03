package com.enterpriseaudit.platform.ingestion;

import com.enterpriseaudit.platform.security.TenantContext;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.*;

@Component
public class DocumentIngestionConsumer {
    private final JdbcTemplate jdbc;
    private final S3Client s3;
    private final VectorStore vectorStore;
    private final String bucket;

    public DocumentIngestionConsumer(JdbcTemplate jdbc, S3Client s3, VectorStore vectorStore,
            @Value("${app.s3.bucket}") String bucket) {
        this.jdbc = jdbc; this.s3 = s3; this.vectorStore = vectorStore; this.bucket = bucket;
    }

    @KafkaListener(topics = "${app.ingestion.topic}", groupId = "audit-ingestion")
    @Transactional
    public void ingest(IngestionMessage message) {
        TenantContext.set(message.tenantId());
        try {
            jdbc.queryForObject("select set_config('app.current_tenant', ?, true)", String.class, message.tenantId().toString());
            process(message);
        }
        finally { TenantContext.clear(); }
    }

    void process(IngestionMessage message) {
        var rows = jdbc.query("SELECT object_key, status FROM documents WHERE id = ? AND tenant_id = ?",
                (rs, row) -> Map.entry(rs.getString("object_key"), rs.getString("status")),
                message.documentId(), message.tenantId());
        if (rows.isEmpty() || "READY".equals(rows.getFirst().getValue())) return;
        jdbc.update("UPDATE documents SET status = 'PROCESSING', error_message = NULL WHERE id = ? AND tenant_id = ?",
                message.documentId(), message.tenantId());
        try (ResponseInputStream<GetObjectResponse> input = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(rows.getFirst().getKey()).build())) {
            String text = extractText(input);
            List<String> chunks = chunk(text, 1800, 250);
            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = Map.of("tenant_id", message.tenantId().toString(),
                        "document_id", message.documentId().toString(), "chunk_index", i);
                docs.add(new Document(UUID.randomUUID().toString(), chunks.get(i), metadata));
            }
            if (!docs.isEmpty()) vectorStore.add(docs);
            jdbc.update("UPDATE documents SET status = 'READY', processed_at = now() WHERE id = ? AND tenant_id = ?",
                    message.documentId(), message.tenantId());
            jdbc.update("INSERT INTO audit_logs(id, tenant_id, action, resource_id) VALUES (?, ?, 'DOCUMENT_INGESTED', ?)",
                    UUID.randomUUID(), message.tenantId(), message.documentId());
        } catch (Exception failure) {
            jdbc.update("UPDATE documents SET status = 'FAILED', error_message = ? WHERE id = ? AND tenant_id = ?",
                    failure.getMessage() == null ? "Document processing failed" : failure.getMessage().substring(0, Math.min(500, failure.getMessage().length())),
                    message.documentId(), message.tenantId());
        }
    }

    static List<String> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + maxChars);
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end);
                if (boundary > start + maxChars / 2) end = boundary;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) result.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private String extractText(InputStream input) throws Exception {
        var parser = new AutoDetectParser();
        var metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(-1);
        var context = new ParseContext();
        var pdfConfig = new PDFParserConfig();
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO);
        context.set(PDFParserConfig.class, pdfConfig);
        parser.parse(input, handler, metadata, context);
        return handler.toString();
    }
}
