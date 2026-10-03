# Plataforma de Governança e Auditoria Empresarial

Plataforma multi-tenant de auditoria documental com ingestão assíncrona, PostgreSQL/pgvector, Kafka, Spring AI e console Next.js. A itenção é desenvolver um estudo que p

> Scaffold para desenvolvimento local. As ferramentas de eSocial e histórico de colaboradores usam adaptadores de demonstração. Não utilize documentos médicos ou de ambiente de trabalho (sintéticos ou reais) em ambientes não confiáveis.

## Componentes

- `backend/`: Java 21, Spring Boot 4, Spring AI 2, Kafka, PostgreSQL, MCP e armazenamento compatível com S3.
- `frontend/`: console Next.js 15 para upload de documentos e perguntas de auditoria em streaming.
- `infra/`: PostgreSQL/pgvector e configuração de serviços locais.
- `docs/`: decisões de arquitetura e notas de segurança local.

## Por que Java e Spring Boot?
A minha base é sempre TypeScript, JavaScript, Tailwind e React. Há a necessidade de investir em outras linguages que aprendi por anos.
O backend neste sistema coordena tarefas que dependem de vários serviços: recebe arquivos, grava no PostgreSQL e no object storage, publica eventos no Kafka e consulta modelos de IA. O Java é ótimo, e oferece uma base madura para esse tipo de serviço, com tipagem estática, ferramentas de diagnóstico e um ecossistema amplo para segurança e mais proteção e consistência para um site. Isso ajuda a manter regras de auditoria e isolamento entre tenants explícitas conforme a aplicação cresce.

Spring Boot reúne a configuração da API, validação de tokens OIDC/JWT, acesso a dados, migrações, Kafka e métricas em uma aplicação. A escolha não significa que Java seja a melhor linguagem para todo sistema; ela atende bem a necessidade deste backend transacional e integrado.

Java 21 também oferece threads virtuais, úteis em certos fluxos com muitas operações bloqueantes de I/O. Elas não aceleram trabalho de CPU por si só. Esta aplicação ainda não habilita esse recurso; qualquer adoção deve vir após medição de carga. Consulte os [requisitos do Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), a [configuração de execução assíncrona](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html) e a [documentação de threads do Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html).

## Por que Mantine?

Eu já trabalho bastante com React, mas não queria manter uma única stack, que consegui avançar. Eu busco aprender mais.

Escolhi o Mantine porque:

**Não é Tailwind.** O Mantine traz componentes prontos (cards, tabelas, alertas, área de upload, menu lateral) com estilo consistente, sem ficar espalhando dezenas de classes utilitárias no JSX.

## Como executar localmente

No fluxo padrão, Docker Compose inicia PostgreSQL/pgvector, Kafka, MinIO e Ollama. A API Java roda no host pelo Maven, e o frontend Next.js roda em outro processo. São três camadas que podem ser iniciadas e depuradas separadamente.

Para executar a API no host:

```powershell
cd backend
mvn spring-boot:run
```

Para executar backend e frontend em containers, o Compose define o perfil `app` e constrói a imagem Java 21 da API e a imagem do console:

```powershell
docker compose --profile app up --build
```

Na primeira execução, prepare `.env` e baixe os modelos Ollama descritos acima antes de iniciar esse perfil. A API fica disponível em `http://localhost:8080` e o console em `http://localhost:3000`. Para desenvolver a interface com atualização automática, execute-a separadamente em `frontend` com `npm run dev`.

## Subir dependências locais

1. Instale Docker Desktop, Java 21 e Maven 3.9+. Copie `.env.example` para `.env` e substitua as senhas locais.
2. Inicie os serviços com `docker compose up -d postgres kafka minio ollama keycloak`.
3. Baixe os modelos locais: `docker compose exec ollama ollama pull llama3.2` e `docker compose exec ollama ollama pull nomic-embed-text`.
4. Inicie a API: `cd backend; mvn spring-boot:run`.
5. Inicie o console web: `cd frontend; npm install; npm run dev`.

## Login

O console abre em **http://localhost:3000/login**. Use as credenciais de demonstração provisionadas no Keycloak local:

- **E-mail:** `auditor@local.demo`
- **Senha:** `demo123`

O token recebido inclui a claim `tenant_id` do tenant local `00000000-0000-0000-0000-000000000001`. Após entrar, a sessão permanece ativa até você clicar em **Sair**.

## Ver só a interface web

```powershell
cd frontend
npm install
npm run dev
```

Abra **http://localhost:3000**. Sem sessão, você será redirecionada para a tela de login. Upload e chat só funcionam com backend, Keycloak e dependências ativas.

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
