"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  FileInput,
  Group,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title,
} from "@mantine/core";

type DocumentOption = { id: string; filename: string };
type Control = { id: string; code: string; version: string; title: string; description: string; sourceUrl: string; effectiveFrom: string | null; reviewStatus: string };
type Finding = { id: string; controlId: string; controlCode: string; documentId: string | null; title: string; evidenceExcerpt: string; state: string; confidence: number | null; reviewNote: string | null; reviewedBy: string | null; createdAt: string };
type Task = { id: string; findingId: string | null; title: string; assigneeEmail: string | null; dueDate: string | null; state: string; category: string; originReference: string | null; sourceEvent: string | null };
type TaskInbox = { open: number; overdue: number; dueToday: number; dueSoon: number; items: Task[] };
type Person = { id: string; externalRef: string; unitCode: string; employmentStatus: string; effectiveDate: string | null };
type ImportItem = { id: string; sourceName: string; importedRows: number; createdAt: string };
type AuditEvent = { id: string; action: string; resourceId: string | null; details: string; createdAt: string };

const api = "/api/backend/api/v1";

export function AuditOperations({ documents }: { documents: DocumentOption[] }) {
  const [controls, setControls] = useState<Control[]>([]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [inbox, setInbox] = useState<TaskInbox>({ open: 0, overdue: 0, dueToday: 0, dueSoon: 0, items: [] });
  const [people, setPeople] = useState<Person[]>([]);
  const [imports, setImports] = useState<ImportItem[]>([]);
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [controlId, setControlId] = useState<string | null>(null);
  const [controlCode, setControlCode] = useState("");
  const [controlVersion, setControlVersion] = useState("");
  const [controlTitle, setControlTitle] = useState("");
  const [controlDescription, setControlDescription] = useState("");
  const [controlSource, setControlSource] = useState("https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-paritaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1");
  const [controlNote, setControlNote] = useState("");
  const [documentId, setDocumentId] = useState<string | null>(null);
  const [findingTitle, setFindingTitle] = useState("");
  const [evidenceExcerpt, setEvidenceExcerpt] = useState("");
  const [reviewNote, setReviewNote] = useState("");
  const [taskTitle, setTaskTitle] = useState("");
  const [assigneeEmail, setAssigneeEmail] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [taskFindingId, setTaskFindingId] = useState<string | null>(null);
  const [taskCategory, setTaskCategory] = useState<string | null>("GENERAL");
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    const [controlResponse, findingResponse, inboxResponse, peopleResponse, importResponse, eventResponse] = await Promise.all([
      fetch(`${api}/controls`, { cache: "no-store" }),
      fetch(`${api}/findings`, { cache: "no-store" }),
      fetch(`${api}/tasks/inbox`, { cache: "no-store" }),
      fetch(`${api}/hr/records?limit=50`, { cache: "no-store" }),
      fetch(`${api}/hr/imports`, { cache: "no-store" }),
      fetch(`${api}/audit/events?limit=50`, { cache: "no-store" }),
    ]);
    const responses = [controlResponse, findingResponse, inboxResponse, peopleResponse, importResponse, eventResponse];
    const failed = responses.find((response) => !response.ok);
    if (failed) throw new Error(`Não foi possível carregar os controles e ações (${failed.status}).`);
    const [nextControls, nextFindings, nextInbox, nextPeople, nextImports, nextEvents] = await Promise.all(
      responses.map((response) => response.json()),
    ) as [Control[], Finding[], TaskInbox, Person[], ImportItem[], AuditEvent[]];
    setControls(nextControls);
    setFindings(nextFindings);
    setInbox(nextInbox);
    setPeople(nextPeople);
    setImports(nextImports);
    setEvents(nextEvents);
    if (!controlId && nextControls.length) setControlId(nextControls[0].id);
  }, [controlId]);

  useEffect(() => {
    void reload().catch((cause: unknown) => setError(cause instanceof Error ? cause.message : "Falha ao carregar auditorias."));
  }, [reload]);

  async function send(path: string, method: string, body: BodyInit, contentType = "application/json") {
    const response = await fetch(`${api}${path}`, {
      method,
      headers: contentType ? { "Content-Type": contentType } : undefined,
      body,
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({})) as { message?: string; error?: string };
      throw new Error(data.message ?? data.error ?? `Falha na operação (${response.status}).`);
    }
    return response;
  }

  async function createFinding(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!controlId) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await send("/findings", "POST", JSON.stringify({
        controlId,
        documentId: documentId || null,
        title: findingTitle,
        evidenceExcerpt,
      }));
      setFindingTitle("");
      setEvidenceExcerpt("");
      setMessage("Achado registrado para revisão humana.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível registrar o achado.");
    } finally {
      setBusy(false);
    }
  }

  async function reviewFinding(id: string, state: string) {
    setBusy(true);
    setError("");
    try {
      await send(`/findings/${id}/review`, "PATCH", JSON.stringify({
        state,
        note: reviewNote,
      }));
      setReviewNote("");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível registrar a revisão.");
    } finally {
      setBusy(false);
    }
  }

  async function createControl(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await send("/controls", "POST", JSON.stringify({
        code: controlCode,
        version: controlVersion,
        title: controlTitle,
        description: controlDescription,
        sourceUrl: controlSource,
      }));
      setControlCode("");
      setControlVersion("");
      setControlTitle("");
      setControlDescription("");
      setMessage("Nova versão criada como rascunho.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível criar a versão.");
    } finally {
      setBusy(false);
    }
  }

  async function markControlReviewed(id: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await send(`/controls/${id}/review`, "PATCH", JSON.stringify({ status: "REVIEWED", note: controlNote }));
      setControlNote("");
      setMessage("Revisão do controle registrada.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível registrar a revisão.");
    } finally {
      setBusy(false);
    }
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await send("/tasks", "POST", JSON.stringify({
        findingId: taskFindingId || null,
        title: taskTitle,
        assigneeEmail: assigneeEmail || null,
        dueDate: dueDate || null,
        category: taskCategory,
      }));
      setTaskTitle("");
      setAssigneeEmail("");
      setDueDate("");
      setMessage("Pendência criada. O aviso está na fila de notificações.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível criar a pendência.");
    } finally {
      setBusy(false);
    }
  }

  async function updateTask(id: string, state: string) {
    setBusy(true);
    setError("");
    try {
      await send(`/tasks/${id}/state`, "PATCH", JSON.stringify({ state }));
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível atualizar a pendência.");
    } finally {
      setBusy(false);
    }
  }

  async function importPeople(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!csvFile) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const body = new FormData();
      body.append("file", csvFile);
      const response = await send("/hr/import", "POST", body, "");
      const result = await response.json() as { importedRows: number };
      setCsvFile(null);
      setMessage(`${result.importedRows} registros de vínculo importados.`);
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível importar o CSV.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack gap="lg" mt="lg">
      {error && <Alert color="red" role="alert">{error}</Alert>}
      {message && <Alert color="teal" role="status">{message}</Alert>}

      <Card withBorder radius="md" padding="lg">
        <Stack gap="md">
          <Group justify="space-between">
            <div>
              <Text size="xs" c="dimmed" tt="uppercase" fw={600}>NR-1 · GRO · PGR</Text>
              <Title order={4}>Controles e evidências</Title>
            </div>
            <Button variant="default" onClick={() => void reload()} disabled={busy}>Atualizar</Button>
          </Group>
          <Alert color="yellow" variant="light">
            O catálogo inicial é rascunho e precisa de validação de profissional de SST antes de qualquer conclusão de conformidade.
          </Alert>
          <TextInput label="Motivo para marcar um controle como revisado" value={controlNote} onChange={(event) => setControlNote(event.currentTarget.value)} maxLength={2000} />
          <SimpleGrid cols={{ base: 1, md: 3 }}>
            {controls.map((control) => (
              <Card key={control.id} withBorder padding="sm">
                <Group justify="space-between" mb="xs">
                  <Badge variant="light">{control.code}</Badge>
                  <Badge color={control.reviewStatus === "REVIEWED" ? "green" : "yellow"} variant="light">
                    {control.reviewStatus === "REVIEWED" ? "Revisado" : "Rascunho"}
                  </Badge>
                </Group>
                <Text size="sm" fw={600}>{control.title}</Text>
                <Text size="xs" c="dimmed" mt="xs">Versão {control.version}{control.effectiveFrom ? ` · vigência ${control.effectiveFrom}` : ""}</Text>
                <Text size="xs" mt="xs">{control.description}</Text>
                <Text component="a" href={control.sourceUrl} target="_blank" rel="noreferrer" size="xs" mt="sm" c="teal">
                  Fonte oficial
                </Text>
                {control.reviewStatus === "DRAFT" && (
                  <Button size="xs" variant="light" mt="sm" disabled={busy || !controlNote.trim()} onClick={() => void markControlReviewed(control.id)}>
                    Registrar revisão SST
                  </Button>
                )}
              </Card>
            ))}
          </SimpleGrid>
          <form onSubmit={createControl}>
            <Stack gap="sm">
              <Title order={5}>Criar nova versão do catálogo</Title>
              <SimpleGrid cols={{ base: 1, md: 2 }}>
                <TextInput label="Código do controle" value={controlCode} onChange={(event) => setControlCode(event.currentTarget.value)} maxLength={80} required />
                <TextInput label="Versão" value={controlVersion} onChange={(event) => setControlVersion(event.currentTarget.value)} maxLength={40} required />
              </SimpleGrid>
              <TextInput label="Título" value={controlTitle} onChange={(event) => setControlTitle(event.currentTarget.value)} maxLength={180} required />
              <Textarea label="Descrição e critério de revisão" value={controlDescription} onChange={(event) => setControlDescription(event.currentTarget.value)} maxLength={5000} required />
              <TextInput label="Fonte oficial (HTTPS)" type="url" value={controlSource} onChange={(event) => setControlSource(event.currentTarget.value)} required />
              <Button type="submit" loading={busy}>Salvar versão como rascunho</Button>
            </Stack>
          </form>
          <form onSubmit={createFinding}>
            <Stack gap="sm">
              <Title order={5}>Registrar achado para revisão humana</Title>
              <Select label="Controle" data={controls.map((control) => ({ value: control.id, label: `${control.code} · ${control.title}` }))} value={controlId} onChange={setControlId} required />
              <Select label="Documento relacionado (opcional)" clearable data={documents.map((document) => ({ value: document.id, label: document.filename }))} value={documentId} onChange={setDocumentId} />
              <TextInput label="Descrição do achado" value={findingTitle} onChange={(event) => setFindingTitle(event.currentTarget.value)} maxLength={180} required />
              <Textarea label="Trecho ou evidência observada" value={evidenceExcerpt} onChange={(event) => setEvidenceExcerpt(event.currentTarget.value)} maxLength={5000} minRows={2} required />
              <Button type="submit" loading={busy} disabled={!controlId}>Registrar achado</Button>
            </Stack>
          </form>
          <Table.ScrollContainer minWidth={760}>
            <Table striped highlightOnHover>
              <Table.Thead><Table.Tr><Table.Th>Achado</Table.Th><Table.Th>Controle</Table.Th><Table.Th>Estado</Table.Th><Table.Th>Revisão humana</Table.Th></Table.Tr></Table.Thead>
              <Table.Tbody>
                {findings.map((finding) => (
                  <Table.Tr key={finding.id}>
                    <Table.Td><Text size="sm" fw={500}>{finding.title}</Text><Text size="xs" c="dimmed">{finding.evidenceExcerpt}</Text></Table.Td>
                    <Table.Td>{finding.controlCode}</Table.Td>
                    <Table.Td><Badge variant="light">{finding.state}</Badge></Table.Td>
                    <Table.Td>
                      <Group gap="xs">
                        <Button size="xs" variant="light" disabled={busy || !reviewNote.trim() || finding.state === "IN_REVIEW"} onClick={() => void reviewFinding(finding.id, "IN_REVIEW")}>Revisar</Button>
                        <Button size="xs" variant="light" color="orange" disabled={busy || !reviewNote.trim() || finding.state === "ACTION_REQUIRED"} onClick={() => void reviewFinding(finding.id, "ACTION_REQUIRED")}>Pedir ação</Button>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          <TextInput label="Justificativa da revisão humana" value={reviewNote} onChange={(event) => setReviewNote(event.currentTarget.value)} maxLength={2000} />
        </Stack>
      </Card>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Card withBorder radius="md" padding="lg">
          <Stack gap="md">
            <div><Text size="xs" c="dimmed" tt="uppercase" fw={600}>Conector RH inicial</Text><Title order={4}>Importar vínculos e unidades</Title></div>
            <Text size="sm" c="dimmed">CSV neutro para validar cobertura enquanto identificamos o fornecedor do piloto. Não inclua prontuários, exames, CPF ou dados clínicos.</Text>
            <Text component="a" href="/rh-import-modelo.csv" download size="sm" c="teal">Baixar modelo CSV</Text>
            <form onSubmit={importPeople}>
              <Stack gap="sm">
                <FileInput label="Arquivo CSV" placeholder="Selecione o arquivo" accept=".csv,text/csv" value={csvFile} onChange={setCsvFile} clearable />
                <Button type="submit" loading={busy} disabled={!csvFile}>Importar vínculos</Button>
              </Stack>
            </form>
            <Text size="xs" c="dimmed">{people.length} registros recentes · {imports.length} importações</Text>
            <Table.ScrollContainer minWidth={460}>
              <Table striped><Table.Thead><Table.Tr><Table.Th>Referência</Table.Th><Table.Th>Unidade</Table.Th><Table.Th>Vínculo</Table.Th></Table.Tr></Table.Thead>
                <Table.Tbody>{people.slice(0, 8).map((person) => <Table.Tr key={person.id}><Table.Td>{person.externalRef}</Table.Td><Table.Td>{person.unitCode}</Table.Td><Table.Td>{person.employmentStatus}</Table.Td></Table.Tr>)}</Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </Stack>
        </Card>

        <Card withBorder radius="md" padding="lg">
          <Stack gap="md">
            <div><Text size="xs" c="dimmed" tt="uppercase" fw={600}>Fila diária · Plano de ação</Text><Title order={4}>Pendências e lembretes</Title></div>
            <SimpleGrid cols={{ base: 2, md: 4 }}>
              <Card withBorder padding="sm"><Text size="xs" c="dimmed">Em aberto</Text><Title order={3}>{inbox.open}</Title></Card>
              <Card withBorder padding="sm"><Text size="xs" c="red">Atrasadas</Text><Title order={3} c="red">{inbox.overdue}</Title></Card>
              <Card withBorder padding="sm"><Text size="xs" c="orange">Vencem hoje</Text><Title order={3}>{inbox.dueToday}</Title></Card>
              <Card withBorder padding="sm"><Text size="xs" c="blue">Próximos 7 dias</Text><Title order={3}>{inbox.dueSoon}</Title></Card>
            </SimpleGrid>
            <form onSubmit={createTask}>
              <Stack gap="sm">
                <TextInput label="Pendência" value={taskTitle} onChange={(event) => setTaskTitle(event.currentTarget.value)} maxLength={180} required />
                <Select label="Achado relacionado (opcional)" clearable data={findings.map((finding) => ({ value: finding.id, label: finding.title }))} value={taskFindingId} onChange={setTaskFindingId} />
                <Select label="Tipo de atividade" data={[{ value: "GENERAL", label: "Pendência geral" }, { value: "PGR_ACTION", label: "Ação do plano PGR" }]} value={taskCategory} onChange={setTaskCategory} />
                <TextInput label="Responsável" type="email" value={assigneeEmail} onChange={(event) => setAssigneeEmail(event.currentTarget.value)} required={taskCategory === "PGR_ACTION"} />
                <TextInput label="Prazo" type="date" value={dueDate} onChange={(event) => setDueDate(event.currentTarget.value)} required={taskCategory === "PGR_ACTION"} />
                <Button type="submit" loading={busy}>Criar pendência</Button>
              </Stack>
            </form>
            <Text size="xs" c="dimmed">Avisos via webhook enviam somente um aviso genérico e prazo, sem título nem conteúdo da evidência.</Text>
            <Table.ScrollContainer minWidth={520}>
              <Table striped><Table.Thead><Table.Tr><Table.Th>Pendência</Table.Th><Table.Th>Categoria</Table.Th><Table.Th>Referência</Table.Th><Table.Th>Prazo</Table.Th><Table.Th>Estado</Table.Th></Table.Tr></Table.Thead>
                <Table.Tbody>{inbox.items.map((task) => <Table.Tr key={task.id}><Table.Td>{task.title}</Table.Td><Table.Td>{task.category === "PGR_ACTION" ? "Plano PGR" : task.category === "HR_CHANGE" ? "Mudança de vínculo" : "Geral"}</Table.Td><Table.Td>{task.originReference ?? "—"}</Table.Td><Table.Td>{task.dueDate ?? "—"}</Table.Td><Table.Td><Select aria-label={`Estado da tarefa ${task.title}`} data={["OPEN", "IN_PROGRESS", "DONE", "CANCELLED"].map((state) => ({ value: state, label: state }))} value={task.state} onChange={(state) => { if (state) void updateTask(task.id, state); }} /></Table.Td></Table.Tr>)}</Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </Stack>
        </Card>
      </SimpleGrid>

      <Card withBorder radius="md" padding="lg">
        <Stack gap="sm">
          <div><Text size="xs" c="dimmed" tt="uppercase" fw={600}>Trilha de auditoria</Text><Title order={4}>Atividade recente</Title></div>
          <Table.ScrollContainer minWidth={600}>
            <Table striped><Table.Thead><Table.Tr><Table.Th>Ação</Table.Th><Table.Th>Recurso</Table.Th><Table.Th>Detalhes</Table.Th><Table.Th>Data</Table.Th></Table.Tr></Table.Thead>
              <Table.Tbody>{events.slice(0, 20).map((event) => <Table.Tr key={event.id}><Table.Td>{event.action}</Table.Td><Table.Td>{event.resourceId?.slice(0, 8) ?? "—"}</Table.Td><Table.Td>{event.details}</Table.Td><Table.Td>{new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(event.createdAt))}</Table.Td></Table.Tr>)}</Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Stack>
      </Card>
    </Stack>
  );
}
