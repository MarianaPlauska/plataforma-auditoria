export type AuditEvent =
  | { type: "token"; content: string; citations: [] }
  | { type: "citations"; content: null; citations: Citation[] }
  | { type: "done"; content: null; citations: [] };

export type Citation = { documentId: string; chunkIndex: number; excerpt: string };

export async function readAuditEvents(
  response: Response,
  onEvent: (event: AuditEvent) => void,
): Promise<void> {
  if (!response.ok) throw new Error(`Request failed (${response.status})`);
  if (!response.body) throw new Error("Streaming response body is unavailable");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "";
  while (true) {
    const { value, done } = await reader.read();
    pending += decoder.decode(value, { stream: !done });
    const frames = pending.split(/\r?\n\r?\n/);
    pending = frames.pop() ?? "";
    for (const frame of frames) {
      const data = frame.split(/\r?\n/).find((line) => line.startsWith("data:"));
      if (!data) continue;
      onEvent(JSON.parse(data.slice(5).trim()) as AuditEvent);
    }
    if (done) break;
  }
}
