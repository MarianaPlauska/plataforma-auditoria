# Enterprise Governance & Audit Agent Platform

Multi-tenant document audit platform with asynchronous ingestion, PostgreSQL/pgvector, Kafka, Spring AI and a Next.js console.

> Local development scaffold. eSocial and employee-history tools currently use demo adapters. Do not use synthetic or real medical/workplace documents in an untrusted environment.

## Components

- `backend/`: Java 21, Spring Boot 4, Spring AI 2, Kafka, PostgreSQL, MCP and S3-compatible storage.
- `frontend/`: Next.js 15 console for document uploads and streaming audit questions.
- `infra/`: PostgreSQL/pgvector and local service configuration.
- `docs/`: architecture decisions and local security notes.

## Start local dependencies

1. Install Docker Desktop, Java 21 and Maven 3.9+. Copy `.env.example` to `.env` and replace local passwords.
2. Start services with `docker compose up -d postgres kafka minio ollama`.
3. Pull local models: `docker compose exec ollama ollama pull llama3.2` and `docker compose exec ollama ollama pull nomic-embed-text`.
4. Start the API: `cd backend; mvn spring-boot:run`.
5. Start the web console: `cd frontend; npm install; npm run dev`.

The API requires a JWT from an OIDC issuer. Set `OIDC_ISSUER_URI` or `OIDC_JWK_SET_URI` to a reachable issuer and include a UUID `tenant_id` claim. For the seeded local tenant, use `00000000-0000-0000-0000-000000000001`. The console accepts the bearer token in its development-only session field. An OIDC login flow is intentionally not bundled.

## Endpoints

- `POST /api/v1/documents/upload` — multipart field `file`; returns document ID and ingestion state.
- `GET /api/v1/documents/{id}` — tenant-scoped ingestion state.
- `POST /api/v1/chat/stream` — JSON `{ "question": "..." }`; streams `text/event-stream` events (`token`, `citations`, `done`).
- `GET /actuator/health` — service health.
- MCP Streamable HTTP server — `/mcp`; protect with the same OIDC resource-server policy before exposing outside localhost.

## Build and checks

- Backend: `cd backend; mvn verify` (integration tests use Testcontainers and require Docker).
- Frontend: `cd frontend; npm ci; npm run lint; npm test; npm run build`.
- CI runs both builds on pushes and pull requests.

The Postgres bootstrap superuser is separate from `audit_app`; the API connects as `audit_app`, a non-superuser role subject to forced RLS. GitHub repository creation is separate from this local scaffold. Create `enterprise-audit-agent-platform` as a **private** repository, then add it as `origin` and push `main`.
