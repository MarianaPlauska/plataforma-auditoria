import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { readAuditEvents } from "./sse";
import Home from "@/app/page";

describe("readAuditEvents", () => {
  it("decodes SSE JSON frames across partial network chunks", async () => {
    const encoder = new TextEncoder();
    const chunks = ["event: token\ndata: {\"type\":\"token\",", "\"content\":\"Olá\",\"citations\":[]}\n\n"];
    const response = new Response(new ReadableStream({
      start(controller) { chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk))); controller.close(); },
    }));
    const onEvent = vi.fn();
    await readAuditEvents(response, onEvent);
    expect(onEvent).toHaveBeenCalledWith({ type: "token", content: "Olá", citations: [] });
  });

  it("rejects unsuccessful API responses", async () => {
    await expect(readAuditEvents(new Response(null, { status: 401 }), vi.fn())).rejects.toThrow("401");
  });

  it("renders the upload and audit chat workspaces", () => {
    render(<Home />);
    expect(screen.getByRole("heading", { name: "Adicionar evidência" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Pergunte ao agente" })).toBeInTheDocument();
  });
});
