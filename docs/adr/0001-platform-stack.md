# ADR 0001: Platform stack

## Status

Accepted for the local scaffold.

## Decision

Use Java 21 with Spring Boot 4 and Spring AI 2 for the API and agent, PostgreSQL 16 with pgvector for transactional and vector data, Kafka for ingestion events, and Next.js 15 for the operator console.

## Consequences

The project stays in one repository and uses established JVM libraries for ingestion and observability. Local models and infrastructure make the development flow reproducible without sending documents to a hosted model provider. The chosen versions are pinned and must be checked together during dependency upgrades.
