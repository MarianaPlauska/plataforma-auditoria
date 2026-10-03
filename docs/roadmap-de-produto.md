# Direção de produto: operações de evidências de conformidade

Revisão inicial: 3 de outubro de 2026.

## Tese

O produto deve ajudar uma equipe a responder, para cada obrigação aplicável: **o que falta, qual evidência sustenta o estado, quem é responsável e até quando a pendência precisa ser resolvida?** O chat é uma forma de explorar evidências, não o produto inteiro.

O fluxo-alvo é:

1. Importar uma obrigação ou regra versionada, com vigência, escopo e fonte oficial.
2. Mapear a obrigação à organização, unidade ou população coberta.
3. Coletar evidências de repositórios, RH e eSocial, sem copiar dados que não sejam necessários.
4. Apontar ausência, divergência ou prazo próximo com referência ao trecho/registro de origem.
5. Abrir uma pendência para uma pessoa, exigir revisão humana e registrar a decisão.
6. Exportar um pacote de auditoria com evidências, versões de regra e histórico de aprovação.

O diferencial é a cadeia verificável entre **regra → evidência → achado → decisão humana → ação corretiva → comprovação**, e não apenas uma resposta gerada por RAG.

## Integrações priorizadas

| Prioridade | Integração | Resultado operacional | Estratégia inicial |
|---|---|---|---|
| P0 | Repositório documental (S3/MinIO; depois SharePoint ou armazenamento escolhido pelo cliente) | Importar políticas, PGR/PCMSO, comprovantes e anexos sem depender de upload manual | S3 já existe no scaffold; adicionar conectores idempotentes, cursor de sincronização, hash, origem e remoção controlada |
| P0 | Catálogo de controles NR-1/GRO/PGR | Tornar a plataforma útil para acompanhar perigos, avaliação, plano de ação, responsável, prazo e revisão | Pacote de controles versionado com vigência e links oficiais; exigir validação de profissional SST antes de marcar conformidade |
| P1 | eSocial SST | Comparar eventos esperados e recibos/resultados recebidos para localizar pendências | Começar por importação e reconciliação somente leitura em ambiente restrito; usar Web Services e autorização/certificado do cliente após validação técnica e contratual |
| P1 | Um sistema de RH/folha usado pelo primeiro cliente (por exemplo TOTVS, Senior ou SAP SuccessFactors) | Sincronizar empresa, unidade, vínculo e identificador de referência para conferir cobertura e evitar duplicação | Escolher após piloto; adapter com API/arquivo, escopo mínimo e idempotência. Evitar importar prontuários ou detalhes clínicos |
| P1 | Gestão de tarefas (Jira, ServiceNow ou webhook corporativo) | Transformar achado em ação com dono, SLA, comentário e comprovante de resolução | Criar tarefa sem conteúdo médico; manter a evidência restrita na plataforma e sincronizar apenas ID, status e prazo |
| P2 | Microsoft Teams ou e-mail corporativo | Avisar sobre prazo e pendência sem expor conteúdo sensível | Mensagens genéricas com link autenticado; respeitar preferências e autorização do cliente |
| P2 | Exportação de auditoria | Entregar pacote reproduzível para auditor, cliente ou revisão interna | PDF/CSV + manifest de hashes, fontes, versões das regras, aprovações e timestamps |

## Sequência de entrega

### Agora: fluxo de evidências utilizável

- Inventário paginado, filtros por estado, contadores e atualização da fila no dashboard.
- Mostrar nome, origem, estado, timestamps, tamanho, falha tratável e referência estável.
- Detalhe por documento com extração, chunks/citações e trilha de ingestão.
- Corrigir/reprocessar falha de OCR e disponibilizar descarte/retenção por tenant.

### Próximo: controles, pendências e revisão humana

- Modelar `control_catalog`, `obligations`, `findings`, `remediation_tasks` e `approvals` com tenant scope e histórico de versão.
- Cada achado deve guardar fonte e localizador, regra/versão aplicada, confiança de extração e estado de revisão.
- Aprovação, rejeição e exceção precisam de ator, justificativa, horário e histórico imutável.
- O agente pode resumir, sugerir classificação e preparar minuta; não deve declarar aptidão médica, diagnosticar ou decidir emprego/benefício.

### Depois: eSocial e conectores de cliente

- Criar `EsocialConnector` atrás de uma interface e começar com dados de homologação e ingestão de recibos/resultados.
- Planejar transmissão como fase separada, com autorização explícita, certificado digital protegido em cofre/HSM, assinatura, trilha de auditoria e aprovação humana antes do envio.
- Não coletar senha gov.br. A documentação do eSocial descreve certificados ICP-Brasil A1/A3 e procurações/perfis para operações; alguns resultados de lote são consultados por Web Service.
- Integrar primeiro o HRIS realmente encontrado no piloto. Não fazer integrações genéricas fictícias com várias marcas.

## Proteções necessárias antes de dados reais de saúde

- S-2220 trata de monitoramento da saúde ocupacional e inclui ASO/exames; S-2230 registra afastamentos. São fluxos de dados pessoais sensíveis ou altamente confidenciais.
- Definir controlador/operador, finalidade/base legal, retenção, atendimento de titulares, exclusão, residência e subprocessadores por cliente antes do piloto.
- Produzir análise de risco/RIPD com o controlador. Aplicar mínimo privilégio por função e por população, separação entre dados clínicos e evidências administrativas, trilha de acesso, criptografia, gestão de chaves e política de retenção.
- Varredura antimalware, validação de conteúdo real (não só extensão), limites anti-zip-bomb, redaction de identificadores em prompts/logs e modelo privado ou local quando o caso exigir.
- Desabilitar treinamento/telemetria com payload do cliente, registrar modelo/prompt/retrieval usados e garantir respostas com citação verificável e abstenção quando faltar evidência.
- Tratar risco psicossocial no nível da organização/trabalho e das medidas preventivas; não criar pontuação individual de saúde mental.

Essas decisões de produto não substituem avaliação jurídica, de privacidade ou de um profissional de Segurança e Saúde no Trabalho.

## Como saber se deixou de ser portfólio

- Percentual de obrigações aplicáveis com evidência recente e aprovada.
- Pendências vencidas e tempo mediano para fechar uma ação corretiva.
- Cobertura da população/unidades esperadas versus eventos conciliados.
- Percentual de afirmações do agente com fonte verificável e taxa de correção humana.
- Minutos do analista para produzir um pacote de auditoria, medidos antes/depois do piloto.
- Falhas e tempo de recuperação de ingestão, separadas por tipo de arquivo e conector.

## Fontes oficiais consultadas

- [NR-1 vigente e material do MTE sobre GRO/PGR e riscos psicossociais](https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-paritaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1) — o MTE lista a redação que entra em vigor em 26 de maio de 2026 e disponibiliza manual e perguntas/respostas do capítulo 1.5.
- [Manual do eSocial Web Geral/SST](https://www.gov.br/esocial/pt-br/empresas/manual-web-geral/manual-web-geral/) — descreve S-2220 como monitoramento da saúde e S-2230 como afastamento temporário; também documenta acessos, perfis e certificados.
- [Documentação técnica e manuais do eSocial](https://www.gov.br/esocial/pt-br/documentacao-tecnica/manuais) — conferir sempre a versão vigente do MOS, dos leiautes e do Manual do Desenvolvedor; a página técnica lista a versão 1.16 do manual do desenvolvedor e a transmissão usa certificado digital ICP-Brasil.
- [Perguntas frequentes do eSocial sobre produção e ambiente de testes](https://www.gov.br/esocial/pt-br/empresas/perguntas-frequentes/perguntas-frequentes-producao-empresas-e-ambiente-de-testes/) — orienta consulta de processamento de lote via Web Service.
- [ANPD: Relatório de Impacto à Proteção de Dados Pessoais](https://www.gov.br/anpd/pt-br/canais_atendimento/agente-de-tratamento/relatorio-de-impacto-a-protecao-de-dados-pessoais-ripd) — risco alto considera, entre outros critérios, dados sensíveis, larga escala, tecnologia inovadora e decisões unicamente automatizadas.
