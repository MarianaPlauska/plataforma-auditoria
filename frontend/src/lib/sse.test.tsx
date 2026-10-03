import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { readAuditEvents } from "./sse";
import { Dashboard } from "@/components/Dashboard";

vi.mock("@/components/AuthProvider", () => ({
  useAuth: () => ({
    session: {
      accessToken: "fake-token",
      email: "auditor@local.demo",
      name: "Auditor Demonstração",
      tenantId: "00000000-0000-0000-0000-000000000001",
    },
    logout: vi.fn(),
  }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

function renderPage() {
  return render(
    <MantineProvider>
      <Dashboard />
    </MantineProvider>,
  );
}

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
    renderPage();
    expect(screen.getByRole("heading", { name: "Adicionar evidência" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Pergunte ao agente" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Registro de evidências" })).toBeInTheDocument();
    expect(screen.getByLabelText("Resumo da ingestão")).toBeInTheDocument();
  });
});
