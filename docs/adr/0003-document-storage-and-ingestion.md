# ADR 0003: Document storage and ingestion

## Status

Accepted for the local scaffold.

## Decision

Store source documents in S3-compatible object storage (MinIO locally). Persist document metadata and an ingestion outbox record transactionally, then publish outbox events to Kafka. Make ingestion idempotent by document ID. Extract text with Tika and use Tesseract as the OCR fallback.

## Consequences

The original document is kept outside PostgreSQL. Outbox retries can deliver duplicate events, so consumers must safely repeat work. Demo object-store credentials must be replaced before deployment.
