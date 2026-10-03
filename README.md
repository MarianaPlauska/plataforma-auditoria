# Plataforma de Governança e Auditoria Empresarial

Plataforma multi-tenant de auditoria documental com ingestão assíncrona, PostgreSQL/pgvector, Kafka, Spring AI e console Next.js.

> Scaffold para desenvolvimento local. As ferramentas de eSocial e histórico de colaboradores usam adaptadores de demonstração. Não utilize documentos médicos ou de ambiente de trabalho (sintéticos ou reais) em ambientes não confiáveis.

## Componentes

- `backend/`: Java 21, Spring Boot 4, Spring AI 2, Kafka, PostgreSQL, MCP e armazenamento compatível com S3.
- `frontend/`: console Next.js 15 para upload de documentos e perguntas de auditoria em streaming.
- `infra/`: PostgreSQL/pgvector e configuração de serviços locais.
- `docs/`: decisões de arquitetura e notas de segurança local.

## Subir dependências locais

1. Instale Docker Desktop, Java 21 e Maven 3.9+. Copie `.env.example` para `.env` e substitua as senhas locais.
2. Inicie os serviços com `docker compose up -d postgres kafka minio ollama`.
3. Baixe os modelos locais: `docker compose exec ollama ollama pull llama3.2` e `docker compose exec ollama ollama pull nomic-embed-text`.
4. Inicie a API: `cd backend; mvn spring-boot:run`.
5. Inicie o console web: `cd frontend; npm install; npm run dev`.

A API exige um JWT de um emissor OIDC. Configure `OIDC_ISSUER_URI` ou `OIDC_JWK_SET_URI` apontando para um emissor acessível e inclua a claim UUID `tenant_id`. Para o tenant local já provisionado, use `00000000-0000-0000-0000-000000000001`. O console aceita o bearer token no campo de sessão exclusivo para desenvolvimento. Um fluxo de login OIDC não está incluído propositalmente.

## Ver só a interface web

Se quiser apenas visualizar a cara do sistema, sem subir toda a stack:

```powershell
cd frontend
npm install
npm run dev
```

Abra **http://localhost:3000** no navegador. Upload e chat só funcionam com backend e dependências ativas.

## Endpoints

- `POST /api/v1/documents/upload` — campo multipart `file`; retorna ID do documento e estado da ingestão.
- `GET /api/v1/documents/{id}` — estado da ingestão escopado por tenant.
- `POST /api/v1/chat/stream` — JSON `{ "question": "..." }`; transmite eventos `text/event-stream` (`token`, `citations`, `done`).
- `GET /actuator/health` — saúde do serviço.
- Servidor MCP Streamable HTTP — `/mcp`; proteja com a mesma política OIDC resource-server antes de expor fora do localhost.

## Build e verificações

- Backend: `cd backend; mvn verify` (testes de integração usam Testcontainers e exigem Docker).
- Frontend: `cd frontend; npm ci; npm run lint; npm test; npm run build`.
- O CI executa os dois builds em pushes e pull requests.

O superusuário de bootstrap do Postgres é separado de `audit_app`; a API conecta como `audit_app`, um papel sem privilégios de superusuário sujeito a RLS forçado.
