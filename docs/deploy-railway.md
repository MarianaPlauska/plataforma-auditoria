# Deploy no Railway (sem Vercel)

O Railway encaixa bem neste projeto porque cada parte (API Java, console Next.js, Postgres, Keycloak) pode ser um serviço Docker separado, sem depender da Vercel.

## Arquitetura sugerida

| Serviço | O que roda | Observação |
|---------|------------|------------|
| `postgres` | PostgreSQL + pgvector | Plugin gerenciado do Railway |
| `keycloak` | Imagem `quay.io/keycloak/keycloak` | Login OIDC |
| `backend` | `backend/Dockerfile` | API Spring Boot |
| `frontend` | `frontend/Dockerfile` | Console Next.js (standalone) |

Kafka, MinIO e Ollama ficam fora do primeiro deploy. Upload e chat com IA exigem esses serviços; o restante do fluxo de auditoria (controles, achados, RH, tarefas) funciona só com Postgres + API + login.

## Passo a passo

### 1. Criar o projeto

1. Acesse [railway.app](https://railway.app) e conecte o repositório GitHub.
2. Crie um projeto vazio e adicione **PostgreSQL**.
3. No Postgres, habilite a extensão `vector` (Railway Postgres 16+ costuma aceitar `CREATE EXTENSION vector`).

### 2. Subir o backend

1. **New Service → GitHub Repo** → selecione este repositório.
2. Em **Settings → Root Directory**, use `backend`.
3. O Railway detecta o `Dockerfile`.
4. Variáveis mínimas:

```env
SPRING_DATASOURCE_URL=${{Postgres.DATABASE_URL}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
OIDC_ISSUER_URI=https://<seu-keycloak>/realms/audit
OIDC_JWK_SET_URI=https://<seu-keycloak>/realms/audit/protocol/openid-connect/certs
S3_ENDPOINT=https://<bucket-s3-compativel>
S3_ACCESS_KEY=...
S3_SECRET_KEY=...
S3_BUCKET=audit-documents
SPRING_KAFKA_BOOTSTRAP_SERVERS=<opcional-no-primeiro-deploy>
```

5. Gere um domínio público e anote a URL (ex.: `https://api-audit.up.railway.app`).

### 3. Subir o Keycloak

1. **New Service → Docker Image** → `quay.io/keycloak/keycloak:26.0.7`.
2. Start command: `start-dev --http-port=8081 --import-realm`.
3. Monte o arquivo `infra/keycloak/audit-realm.json` ou configure o realm manualmente.
4. Ajuste `redirectUris` e `webOrigins` do client `audit-console` para a URL pública do frontend.

### 4. Subir o frontend

1. **New Service → GitHub Repo** → root directory `frontend`.
2. Variáveis:

```env
NEXT_PUBLIC_API_BASE_URL=https://<url-do-backend>
OIDC_ISSUER_URI=https://<url-do-keycloak>/realms/audit
OIDC_CLIENT_ID=audit-console
```

3. Gere domínio público para o console.

### 5. Conferir

- `GET https://<backend>/actuator/health` → `UP`
- Abra `https://<frontend>/login` e entre com a conta demo
- Crie um controle ou importe o CSV de RH para validar o tenant

## Alternativas à Vercel

- **Render**: fluxo parecido (Web Service + Postgres).
- **Fly.io**: bom se quiser tudo em containers com `fly.toml`.
- **VPS + Docker Compose**: use `docker compose --profile app up` em um servidor Linux.

## CI

O GitHub Actions continua rodando `mvn verify` e o build do frontend. O deploy no Railway pode ser manual pelo painel ou com `railway up` após `railway login`.
