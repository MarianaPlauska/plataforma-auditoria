# Plataforma de Governança e Auditoria Empresarial

Plataforma multi-tenant para apoiar auditorias de SST e governança documental. O fluxo inclui catálogo versionado de controles NR-1/GRO/PGR, registro de achados e evidências, revisão humana, importação mínima de dados de RH e acompanhamento de tarefas. Documentos seguem por ingestão assíncrona, com PostgreSQL/pgvector, Kafka, Spring AI e console Next.js.

> Scaffold para desenvolvimento local. As ferramentas de eSocial e histórico de colaboradores usam adaptadores de demonstração. Não utilize documentos médicos ou de ambiente de trabalho (sintéticos ou reais) em ambientes não confiáveis.

## Componentes

- `backend/`: Java 21, Spring Boot 4, Spring AI 2, Kafka, PostgreSQL, MCP e armazenamento compatível com S3.
- `frontend/`: console Next.js 15 para upload de documentos e perguntas de auditoria em streaming.
- `infra/`: PostgreSQL/pgvector e configuração de serviços locais.
- `docs/`: decisões de arquitetura e notas de segurança local.

## Fluxos de auditoria, RH e tarefas

- **Controles:** mantenha versões do catálogo NR-1/GRO/PGR por tenant. Novos controles começam como rascunho; uma pessoa autorizada registra revisão e justificativa antes de marcá-los como revisados. O conteúdo de exemplo não substitui validação por profissional de SST ou orientação jurídica.
- **Achados e evidências:** registre trechos de evidência associados a um controle e documento; o estado e a justificativa da revisão ficam no histórico de auditoria.
- **Importação de RH:** a primeira integração é por CSV com cabeçalho `external_ref,unit_code,employment_status,effective_date`. Importamos somente identificador de referência, unidade, situação e data efetiva; não importar prontuários nem dados clínicos. `HrConnector` é a interface para adicionar depois um adaptador TOTVS, Senior, SAP ou outro escolhido com o cliente piloto.
- **Tarefas e avisos:** achados podem gerar tarefas atribuídas e com prazo. Um outbox tenta entregar eventos a um webhook configurado; o aviso não inclui conteúdo de saúde, evidência ou e-mail do responsável. Sem webhook, as tarefas continuam disponíveis no sistema.
- **Fila diária e gatilhos de RH:** `GET /api/v1/tasks/inbox` resume tarefas em aberto, atrasadas, vencendo hoje e nos próximos sete dias. Importar um vínculo ativo novo, mudar unidade ou alterar situação cadastral cria uma tarefa genérica de revisão SST; reimportar dados sem mudança não duplica a tarefa. O aviso externo não leva identificador do vínculo, nome, prontuário ou dado clínico. O prazo inicial é sete dias após a importação.
- **Lembretes do plano de ação:** tarefas categorizadas como ação do PGR aparecem junto das demais na fila. Com `TASK_WEBHOOK_URL` configurado, o serviço envia um aviso até três dias antes do prazo e repete diariamente enquanto estiver atrasado. O webhook recebe evento, ID da tarefa, prazo e URL do sistema, sem título ou dados do colaborador.
- **Autenticação:** o navegador usa OIDC Authorization Code + PKCE no Keycloak local. A sessão fica em cookie HttpOnly; o token não é exposto ao JavaScript. O servidor Next.js encaminha as chamadas autenticadas para a API.

As APIs principais estão sob `/api/v1/controls`, `/api/v1/findings`, `/api/v1/hr/import`, `/api/v1/hr/records`, `/api/v1/tasks` e `/api/v1/audit/events`. Todas derivam o tenant da claim `tenant_id` do JWT validado.

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

O console abre em **http://localhost:3000/login** e redireciona para o Keycloak local. Entre com a conta de demonstração provisionada no realm local. As credenciais estão definidas na configuração do Keycloak em `infra/keycloak`; altere-as para qualquer ambiente compartilhado. A claim `tenant_id` da conta demo identifica o tenant `00000000-0000-0000-0000-000000000001`.

Para desenvolvimento do frontend fora do Compose, copie `frontend/.env.example` para `frontend/.env.local`. Configure `APP_BASE_URL`, `BACKEND_API_URL`, `OIDC_ISSUER_URI`, `OIDC_INTERNAL_ISSUER_URI`, `OIDC_CLIENT_ID` e um `AUTH_SESSION_SECRET` aleatório com pelo menos 32 caracteres. O `.env.example` na raiz é consumido pelo Compose.

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
- `GET /api/v1/controls`, `GET /api/v1/findings`, `GET /api/v1/tasks` e `GET /api/v1/audit/events` — consulta escopada pelo tenant autenticado.
- `GET /api/v1/tasks/inbox` — contadores e fila ordenada por urgência.
- `POST /api/v1/hr/import` — importa CSV mínimo de RH; requer papel `AUDIT_ADMIN` ou `HR_INTEGRATION`.
- `GET /actuator/health` — saúde do serviço.
- Servidor MCP Streamable HTTP — `/mcp`; proteja com a mesma política OIDC resource-server antes de expor fora do localhost.

## Build e verificações

- Backend: `cd backend; mvn verify` (testes de integração usam Testcontainers e exigem Docker).
- Frontend: `cd frontend; npm ci; npm run lint; npm test; npm run build`.
- O CI executa os dois builds em pushes e pull requests.

O superusuário de bootstrap do Postgres é separado de `audit_app`; a API conecta como `audit_app`, um papel sem privilégios de superusuário sujeito a RLS forçado.
