"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  ActionIcon,
  Alert,
  AppShell,
  Badge,
  Box,
  Button,
  Card,
  Container,
  Divider,
  Group,
  Menu,
  Paper,
  ScrollArea,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  Title,
  Tooltip,
  useMantineColorScheme,
} from "@mantine/core";
import { Dropzone, MIME_TYPES } from "@mantine/dropzone";
import {
  IconAlertCircle,
  IconCloudUpload,
  IconFileDescription,
  IconHome,
  IconLogout,
  IconMessage,
  IconMoon,
  IconRefresh,
  IconSend,
  IconSun,
  IconUpload,
  IconUser,
} from "@tabler/icons-react";
import { useAuth } from "@/components/AuthProvider";
import { AuditEvent, Citation, readAuditEvents } from "@/lib/sse";
import { AuditOperations } from "@/components/AuditOperations";

type UploadResult = { id: string; status: string; message?: string };
type Message = { role: "assistant" | "user"; text: string; citations?: Citation[] };
type DocumentRow = { id: string; filename: string; sizeBytes: number; status: string; createdAt: string };
type DocumentPage = { items: DocumentRow[]; totalElements: number };
type DocumentMetrics = { total: number; received: number; processing: number; ready: number; failed: number };

const emptyMetrics: DocumentMetrics = { total: 0, received: 0, processing: 0, ready: 0, failed: 0 };
const apiBase = "/api/backend";

function statusColor(status: string): string {
  switch (status.toUpperCase()) {
    case "READY": return "green";
    case "FAILED": return "red";
    case "PROCESSING": return "yellow";
    default: return "gray";
  }
}

function statusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case "READY": return "Pronto";
    case "FAILED": return "Falhou";
    case "PROCESSING": return "Processando";
    case "RECEIVED": return "Recebido";
    default: return status;
  }
}

function ColorSchemeToggle() {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const dark = colorScheme === "dark";
  return (
    <Tooltip label={dark ? "Modo claro" : "Modo escuro"}>
      <ActionIcon variant="subtle" size="lg" onClick={() => toggleColorScheme()} aria-label="Alternar tema">
        {dark ? <IconSun size={18} /> : <IconMoon size={18} />}
      </ActionIcon>
    </Tooltip>
  );
}

export function Dashboard() {
  const { session, logout } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  const [upload, setUpload] = useState<UploadResult | null>(null);
  const [question, setQuestion] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [dataError, setDataError] = useState("");
  const [documents, setDocuments] = useState<DocumentRow[]>([]);
  const [metrics, setMetrics] = useState<DocumentMetrics>(emptyMetrics);
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  function handleLogout() {
    void logout();
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoadingDocuments(true);
    setDataError("");
    Promise.all([
      fetch(`${apiBase}/api/v1/documents?page=0&size=8`, { signal: controller.signal }),
      fetch(`${apiBase}/api/v1/documents/summary`, { signal: controller.signal }),
    ]).then(async ([listResponse, summaryResponse]) => {
      if (!listResponse.ok || !summaryResponse.ok) {
        throw new Error(`Não foi possível carregar os dados (${!listResponse.ok ? listResponse.status : summaryResponse.status})`);
      }
      const [list, summary] = await Promise.all([
        listResponse.json() as Promise<DocumentPage>,
        summaryResponse.json() as Promise<DocumentMetrics>,
      ]);
      setDocuments(list.items);
      setMetrics(summary);
    }).catch((cause: unknown) => {
      if (cause instanceof DOMException && cause.name === "AbortError") return;
      setDataError(cause instanceof Error ? cause.message : "Falha ao carregar os documentos");
    }).finally(() => {
      if (!controller.signal.aborted) setLoadingDocuments(false);
    });
    return () => controller.abort();
  }, [refreshKey]);

  async function uploadDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    setBusy(true); setError(""); setUpload(null);
    try {
      const body = new FormData(); body.append("file", file);
      const response = await fetch(`${apiBase}/api/v1/documents/upload`, {
        method: "POST", body,
      });
      if (!response.ok) throw new Error(`Falha no envio (${response.status})`);
      const result = (await response.json()) as UploadResult;
      setUpload(result);
      setRefreshKey((key) => key + 1);
      void pollStatus(result.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha no envio do documento");
    } finally { setBusy(false); }
  }

  async function pollStatus(id: string) {
    const terminal = new Set(["READY", "FAILED"]);
    for (let attempt = 0; attempt < 60; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      try {
        const response = await fetch(`${apiBase}/api/v1/documents/${id}`, {
        });
        if (!response.ok) continue;
        const result = (await response.json()) as UploadResult;
        setUpload(result);
        if (terminal.has(result.status)) {
          setRefreshKey((key) => key + 1);
          return;
        }
      } catch { /* continua consultando */ }
    }
  }

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!question.trim() || busy) return;
    const prompt = question.trim();
    setQuestion(""); setError(""); setBusy(true);
    setMessages((previous) => [...previous, { role: "user", text: prompt }, { role: "assistant", text: "" }]);
    try {
      const response = await fetch(`${apiBase}/api/v1/chat/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ question: prompt }),
      });
      await readAuditEvents(response, (event: AuditEvent) => {
        setMessages((previous) => {
          const next = [...previous];
          const last = next.at(-1);
          if (last?.role !== "assistant") return previous;
          if (event.type === "token") next[next.length - 1] = { ...last, text: last.text + event.content };
          if (event.type === "citations") next[next.length - 1] = { ...last, citations: event.citations };
          return next;
        });
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha ao consultar o agente");
    } finally { setBusy(false); }
  }

  const tenantLabel = session?.tenantId
    ? `Tenant ${session.tenantId.slice(0, 8)}…`
    : "Tenant ativo";

  return (
    <AppShell navbar={{ width: 240, breakpoint: "sm" }} padding="md" header={{ height: 64 }}>
      <AppShell.Header px="md">
        <Group h="100%" justify="space-between">
          <Stack gap={0}>
            <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Governança e auditoria</Text>
            <Title order={3} fw={600}>Visão geral</Title>
          </Stack>
          <Group gap="sm">
            <Badge variant="light" color="blue" size="lg">{tenantLabel}</Badge>
            <ColorSchemeToggle />
            <Menu shadow="md" width={220}>
              <Menu.Target>
                <Button variant="default" leftSection={<IconUser size={16} />}>
                  {session?.name ?? session?.email ?? "Conta"}
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{session?.email}</Menu.Label>
                <Menu.Item leftSection={<IconLogout size={16} />} onClick={handleLogout}>
                  Sair
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md">
        <Stack gap="xs">
          <Group gap="sm" mb="md">
            <Box w={36} h={36} bg="blue.6" c="white" style={{ borderRadius: 8, display: "grid", placeItems: "center" }} fw={700} fz="sm">
              EA
            </Box>
            <div>
              <Text fw={600} size="sm">Audit Desk</Text>
              <Text size="xs" c="dimmed">Área de trabalho</Text>
            </div>
          </Group>
          <Button variant="light" leftSection={<IconHome size={16} />} justify="flex-start">Visão geral</Button>
          <Button variant="subtle" color="gray" leftSection={<IconUpload size={16} />} justify="flex-start" component="a" href="#evidence">Evidências</Button>
          <Button variant="subtle" color="gray" leftSection={<IconMessage size={16} />} justify="flex-start" component="a" href="#assistant">Assistente</Button>
        </Stack>
      </AppShell.Navbar>

      <AppShell.Main bg="gray.0">
        <Container size="xl" py="md">
          <Paper p="xl" radius="md" mb="lg" withBorder>
            <Stack gap="xs">
              <Text size="sm" c="dimmed" tt="uppercase" fw={600}>Plataforma de governança</Text>
              <Title order={2} fw={600}>Da evidência à ação, com contexto</Title>
              <Text c="dimmed" maw={640}>
                Organize documentos, acompanhe a ingestão e consulte o agente com respostas vinculadas às fontes.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="md" mb="lg" aria-label="Resumo da ingestão">
            <MetricCard label="Documentos recebidos" value={metrics.total} hint="no tenant atual" />
            <MetricCard label="Indexados" value={metrics.ready} hint="disponíveis para consulta" color="green" />
            <MetricCard label="Em processamento" value={metrics.received + metrics.processing} hint={`${metrics.received} recebidos · ${metrics.processing} processando`} />
            <MetricCard label="Com falha" value={metrics.failed} hint="precisam de atenção" color={metrics.failed ? "red" : undefined} />
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg" mb="lg">
            <Card withBorder radius="md" padding="lg" id="evidence">
              <Stack gap="md">
                <Group justify="space-between">
                  <Title order={4}>Adicionar evidência</Title>
                  <Badge variant="outline" color="gray">PDF, DOCX, imagens</Badge>
                </Group>
                <form onSubmit={uploadDocument}>
                  <Stack gap="md">
                    <Dropzone
                      onDrop={(files) => setFile(files[0] ?? null)}
                      accept={[MIME_TYPES.pdf, MIME_TYPES.doc, MIME_TYPES.docx, MIME_TYPES.png, MIME_TYPES.jpeg, "image/tiff"]}
                      maxFiles={1}
                      radius="md"
                    >
                      <Group justify="center" gap="xl" mih={120} style={{ pointerEvents: "none" }}>
                        <Dropzone.Accept><IconCloudUpload size={40} stroke={1.5} /></Dropzone.Accept>
                        <Dropzone.Reject><IconAlertCircle size={40} stroke={1.5} /></Dropzone.Reject>
                        <Dropzone.Idle><IconFileDescription size={40} stroke={1.5} /></Dropzone.Idle>
                        <Stack gap={4} align="center">
                          <Text size="sm" fw={500}>{file?.name ?? "Arraste um arquivo ou clique para selecionar"}</Text>
                          <Text size="xs" c="dimmed">Até 1 arquivo por envio</Text>
                        </Stack>
                      </Group>
                    </Dropzone>
                    <Button type="submit" leftSection={<IconUpload size={16} />} loading={busy} disabled={!file}>
                      Enviar para ingestão
                    </Button>
                  </Stack>
                </form>
                {upload && (
                  <Alert variant="light" color={statusColor(upload.status)} title={statusLabel(upload.status)}>
                    {upload.message ?? upload.id}
                  </Alert>
                )}
              </Stack>
            </Card>

            <Card withBorder radius="md" padding="lg" id="assistant">
              <Stack gap="md" h="100%">
                <Group justify="space-between">
                  <Title order={4}>Pergunte ao agente</Title>
                  <Badge color="green" variant="light">Assistente de auditoria</Badge>
                </Group>
                <ScrollArea flex={1} mih={320} mah={420} type="auto" offsetScrollbars>
                  {messages.length === 0 ? (
                    <Stack align="center" py="xl" gap="sm">
                      <Text fw={600}>Pronto para revisar evidências</Text>
                      <Text size="sm" c="dimmed" ta="center" maw={360}>
                        Faça uma pergunta sobre os documentos concluídos. O agente incluirá trechos de origem nas respostas.
                      </Text>
                      <Button variant="light" onClick={() => setQuestion("Quais evidências de conformidade aparecem nos documentos?")}>
                        Sugestão: evidências de conformidade
                      </Button>
                    </Stack>
                  ) : (
                    <Stack gap="sm">
                      {messages.map((message, index) => (
                        <Paper key={`${index}-${message.role}`} p="md" radius="md" bg={message.role === "user" ? "blue.0" : "gray.0"} withBorder={message.role === "assistant"}>
                          <Text size="xs" c="dimmed" tt="uppercase" mb={4}>
                            {message.role === "user" ? "Você" : "Agente de auditoria"}
                          </Text>
                          <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>
                            {message.text || (busy && message.role === "assistant" ? "Analisando evidências…" : "")}
                          </Text>
                          {message.citations?.map((citation) => (
                            <Paper key={`${citation.documentId}-${citation.chunkIndex}`} p="sm" mt="sm" withBorder radius="sm">
                              <Text size="xs" fw={600}>Documento {citation.documentId.slice(0, 8)}</Text>
                              <Text size="xs" c="dimmed">{citation.excerpt}</Text>
                            </Paper>
                          ))}
                        </Paper>
                      ))}
                    </Stack>
                  )}
                </ScrollArea>
                <form onSubmit={ask}>
                  <Group align="flex-end" gap="sm">
                    <Textarea
                      flex={1}
                      aria-label="Pergunta ao agente"
                      placeholder="Pergunte sobre os documentos…"
                      value={question}
                      onChange={(event) => setQuestion(event.currentTarget.value)}
                      autosize
                      minRows={1}
                      maxRows={4}
                    />
                    <ActionIcon type="submit" size="xl" variant="filled" disabled={busy || !question.trim()} aria-label="Enviar pergunta">
                      <IconSend size={18} />
                    </ActionIcon>
                  </Group>
                </form>
              </Stack>
            </Card>
          </SimpleGrid>

          <AuditOperations documents={documents.filter((document) => document.status === "READY")} />

          <Card withBorder radius="md" padding="lg">
            <Group justify="space-between" mb="md" wrap="wrap">
              <Stack gap={4}>
                <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Acervo documental</Text>
                <Title order={4} id="document-register-title">Registro de evidências</Title>
                <Text size="sm" c="dimmed">Veja o que já entrou na fila e o que o agente consegue consultar.</Text>
              </Stack>
              <Button variant="default" leftSection={<IconRefresh size={16} />} onClick={() => setRefreshKey((key) => key + 1)} loading={loadingDocuments}>
                Atualizar lista
              </Button>
            </Group>

            {dataError && <Alert color="red" mb="md" icon={<IconAlertCircle size={16} />}>{dataError}</Alert>}
            {documents.length === 0 && !loadingDocuments ? (
              <Alert variant="light" color="blue">Nenhum documento recebido ainda. Envie uma evidência para iniciar a fila.</Alert>
            ) : (
              <Table.ScrollContainer minWidth={540}>
                <Table striped highlightOnHover withTableBorder>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Documento</Table.Th>
                      <Table.Th>Estado</Table.Th>
                      <Table.Th>Recebido em</Table.Th>
                      <Table.Th>Tamanho</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {documents.map((document) => (
                      <Table.Tr key={document.id}>
                        <Table.Td>
                          <Text size="sm" fw={500}>{document.filename}</Text>
                          <Text size="xs" c="dimmed">{document.id.slice(0, 8)}</Text>
                        </Table.Td>
                        <Table.Td>
                          <Badge color={statusColor(document.status)} variant="light">{statusLabel(document.status)}</Badge>
                        </Table.Td>
                        <Table.Td>
                          {new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(document.createdAt))}
                        </Table.Td>
                        <Table.Td>{(document.sizeBytes / (1024 * 1024)).toFixed(2)} MB</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            )}
            <Divider my="md" />
            <Group justify="space-between">
              <Text size="xs" c="dimmed">Exibindo {documents.length} de {metrics.total} documentos</Text>
              <Text size="xs" c="dimmed">Dados filtrados no backend pelo tenant autenticado · RLS ativo</Text>
            </Group>
          </Card>

          {error && <Alert color="red" mt="md" icon={<IconAlertCircle size={16} />}>{error}</Alert>}
        </Container>
      </AppShell.Main>
    </AppShell>
  );
}

function MetricCard({ label, value, hint, color }: { label: string; value: number; hint: string; color?: string }) {
  return (
    <Paper withBorder p="lg" radius="md">
      <Text size="sm" c="dimmed">{label}</Text>
      <Title order={2} mt={4} c={color}>{value}</Title>
      <Text size="xs" c="dimmed" mt={4}>{hint}</Text>
    </Paper>
  );
}
