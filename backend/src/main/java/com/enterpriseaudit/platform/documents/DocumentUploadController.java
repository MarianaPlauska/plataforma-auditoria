package com.enterpriseaudit.platform.documents;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.enterpriseaudit.platform.security.TenantContext;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.EmptyResultDataAccessException;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentUploadController {
    private final JdbcTemplate jdbc;
    private final S3Client s3;
    private final String bucket;
    private final long maxBytes;

    public DocumentUploadController(JdbcTemplate jdbc, S3Client s3,
            @org.springframework.beans.factory.annotation.Value("${app.s3.bucket}") String bucket,
            @org.springframework.beans.factory.annotation.Value("${app.upload.max-bytes}") long maxBytes) {
        this.jdbc = jdbc; this.s3 = s3; this.bucket = bucket; this.maxBytes = maxBytes;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        if (file.getSize() > maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds configured upload limit");
        String filename = safeFilename(file.getOriginalFilename());
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!Set.of("pdf", "doc", "docx", "png", "jpg", "jpeg", "tif", "tiff").contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document type");
        }
        UUID tenantId = TenantContext.requireTenantId();
        UUID documentId = UUID.randomUUID();
        String key = tenantId + "/" + documentId + "/" + filename;
        ensureBucket();
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType(file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType())
                        .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        jdbc.update("""
                INSERT INTO documents(id, tenant_id, original_filename, object_key, content_type, size_bytes, status)
                VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED')
                """, documentId, tenantId, filename, key,
                file.getContentType(), file.getSize());
        jdbc.update("INSERT INTO ingestion_outbox(id, tenant_id, document_id, event_type, payload) VALUES (?, ?, ?, 'DOCUMENT_INGESTION_REQUESTED', ?::jsonb)",
                UUID.randomUUID(), tenantId, documentId, "{\"documentId\":\"" + documentId + "\",\"tenantId\":\"" + tenantId + "\"}");
        jdbc.update("INSERT INTO audit_logs(id, tenant_id, action, resource_id) VALUES (?, ?, 'DOCUMENT_UPLOADED', ?)",
                UUID.randomUUID(), tenantId, documentId);
        return new DocumentResponse(documentId, DocumentState.RECEIVED, "Upload stored; ingestion queued");
    }

    @GetMapping("/{id}")
    public DocumentResponse status(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        try {
            return jdbc.queryForObject("SELECT id, status FROM documents WHERE id = ? AND tenant_id = ?",
                    (rs, row) -> new DocumentResponse(rs.getObject("id", UUID.class),
                            DocumentState.valueOf(rs.getString("status")), null), id, tenantId);
        } catch (EmptyResultDataAccessException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
    }

    private void ensureBucket() {
        try { s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build()); }
        catch (S3Exception missing) {
            if (missing.statusCode() != 404) throw missing;
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private String safeFilename(String input) {
        String name = input == null || input.isBlank() ? "upload.bin" : input.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record DocumentResponse(UUID id, DocumentState status, String message) {}
}
