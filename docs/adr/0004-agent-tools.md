# ADR 0004: RAG and MCP tools

## Status

Accepted for the local scaffold.

## Decision

Use Spring AI `ChatClient`, Ollama and `PgVectorStore` for RAG. The agent calls internal service-backed tool callbacks. Expose the same demo business operations through an authenticated MCP Streamable HTTP server using Spring AI MCP annotations. Keep chat SSE as a separate application endpoint.

## Consequences

Every RAG request must carry tenant filters. MCP HTTP transport does not provide application authentication by itself, so Spring Security protects its endpoint. eSocial and audit-history adapters return synthetic demo data until real integration contracts are supplied.
