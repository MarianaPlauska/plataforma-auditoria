"use client";

import { FormEvent, useState } from "react";
import { AuditEvent, Citation, readAuditEvents } from "@/lib/sse";

type UploadResult = { id: string; status: string; message?: string };
type Message = { role: "assistant" | "user"; text: string; citations?: Citation[] };

const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export default function Home() {
  const [token, setToken] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [upload, setUpload] = useState<UploadResult | null>(null);
  const [question, setQuestion] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function uploadDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || !token) return;
    setBusy(true); setError(""); setUpload(null);
    try {
      const body = new FormData(); body.append("file", file);
      const response = await fetch(`${apiBase}/api/v1/documents/upload`, {
        method: "POST", headers: { Authorization: `Bearer ${token}` }, body,
      });
      if (!response.ok) throw new Error(`Upload failed (${response.status})`);
      const result = (await response.json()) as UploadResult;
      setUpload(result);
      void pollStatus(result.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Upload failed");
    } finally { setBusy(false); }
  }

  async function pollStatus(id: string) {
    const terminal = new Set(["READY", "FAILED"]);
    for (let attempt = 0; attempt < 60; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      try {
        const response = await fetch(`${apiBase}/api/v1/documents/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) continue;
        const result = (await response.json()) as UploadResult;
        setUpload(result);
        if (terminal.has(result.status)) return;
      } catch { /* Keep polling through short local service restarts. */ }
    }
  }

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!question.trim() || !token || busy) return;
    const prompt = question.trim();
    setQuestion(""); setError(""); setBusy(true);
    setMessages((previous) => [...previous, { role: "user", text: prompt }, { role: "assistant", text: "" }]);
    try {
      const response = await fetch(`${apiBase}/api/v1/chat/stream`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", Accept: "text/event-stream" },
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
      setError(cause instanceof Error ? cause.message : "Chat request failed");
    } finally { setBusy(false); }
  }

  return (
    <main className="shell">
      <aside className="rail"><div className="brand">EA</div><span className="rail-active">⌂</span><span>▤</span><span>◷</span><div className="rail-bottom">MP</div></aside>
      <section className="workspace">
        <header className="topbar"><div><span className="eyebrow">GOVERNANCE WORKSPACE</span><h1>Audit Desk</h1></div><div className="tenant-pill"><span className="status-dot"/> Local demo tenant</div></header>
        <div className="content">
          <section className="intro"><div><div className="eyebrow">ENTERPRISE AUDIT AGENT</div><h2>Documentos, contexto e conformidade.</h2><p>Envie evidências para ingestão e converse com o agente sobre o conteúdo indexado.</p></div><div className="intro-mark">✳</div></section>
          <div className="columns">
            <section className="panel upload-panel">
              <div className="panel-heading"><div><span className="step">01</span><h3>Adicionar evidência</h3></div><span className="subtle">PDF, DOCX, imagens</span></div>
              <label className="field-label" htmlFor="token">OIDC access token <span>desenvolvimento</span></label>
              <input id="token" className="text-input" type="password" autoComplete="off" value={token} onChange={(event) => setToken(event.target.value)} placeholder="Cole um JWT com claim tenant_id" />
              <form onSubmit={uploadDocument}>
                <label className="dropzone" htmlFor="file"><span className="upload-icon">↑</span><strong>{file?.name ?? "Escolha um arquivo"}</strong><span>ou arraste para esta área</span><input id="file" type="file" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.tif,.tiff" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /></label>
                <button className="button button-dark full" disabled={!file || !token || busy}>{busy ? "Processando…" : "Enviar para ingestão"}<span>↗</span></button>
              </form>
              {upload && <div className="upload-state"><span className={`state-dot ${upload.status.toLowerCase()}`} /> <div><strong>{upload.status}</strong><small>{upload.message ?? upload.id}</small></div></div>}
            </section>
            <section className="panel chat-panel">
              <div className="panel-heading"><div><span className="step">02</span><h3>Pergunte ao agente</h3></div><span className="online-pill"><i/> AGENT READY</span></div>
              <div className="conversation">
                {messages.length === 0 ? <div className="empty-state"><div className="sparkle">✳</div><strong>Pronto para revisar evidências</strong><p>Faça uma pergunta sobre os documentos concluídos. O agente incluirá trechos de origem nas respostas.</p><button type="button" className="suggestion" onClick={() => setQuestion("Quais evidências de conformidade aparecem nos documentos?")}>“Quais evidências de conformidade aparecem nos documentos?” <span>↗</span></button></div> : messages.map((message, index) => <article className={`message ${message.role}`} key={`${index}-${message.role}`}><div className="message-label">{message.role === "user" ? "VOCÊ" : "AUDIT AGENT"}</div><p>{message.text || (busy && message.role === "assistant" ? "Analisando evidências…" : "")}</p>{message.citations?.map((citation) => <div className="citation" key={`${citation.documentId}-${citation.chunkIndex}`}><span>↗</span><div><strong>Documento {citation.documentId.slice(0, 8)}</strong><small>{citation.excerpt}</small></div></div>)}</article>)}
              </div>
              <form className="prompt-form" onSubmit={ask}><input aria-label="Pergunta ao agente" value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Pergunte sobre os documentos…" /><button aria-label="Enviar pergunta" disabled={busy || !question.trim() || !token}>↑</button></form>
            </section>
          </div>
          {error && <div className="error-banner" role="alert">{error}</div>}
          <footer><span>◆ Seguro por tenant · RLS ativo</span><span>Evidence first. Decisions with context.</span></footer>
        </div>
      </section>
    </main>
  );
}
