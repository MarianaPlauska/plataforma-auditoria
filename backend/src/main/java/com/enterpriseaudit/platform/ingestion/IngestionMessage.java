package com.enterpriseaudit.platform.ingestion;

import java.util.UUID;

public record IngestionMessage(UUID tenantId, UUID documentId) {}
