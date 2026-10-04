import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { getBaseUrl, refreshSession } from "@/lib/oidc";

type RouteContext = { params: Promise<{ path: string[] }> };

const allowedPaths = [
  "/api/v1/documents",
  "/api/v1/chat",
  "/api/v1/controls",
  "/api/v1/findings",
  "/api/v1/tasks",
  "/api/v1/hr",
];

async function proxy(request: Request, context: RouteContext) {
  const origin = request.headers.get("origin");
  if (request.method !== "GET" && request.method !== "HEAD" && origin !== getBaseUrl(request).origin) {
    return NextResponse.json({ error: "Origem inválida." }, { status: 403 });
  }

  const { path } = await context.params;
  const requestPath = `/${path.join("/")}`;
  if (!allowedPaths.some((prefix) => requestPath === prefix || requestPath.startsWith(`${prefix}/`))) {
    return NextResponse.json({ error: "Rota não disponível." }, { status: 404 });
  }

  const session = await getSession();
  if (!session.accessToken || !session.expiresAt) {
    return NextResponse.json({ error: "Sessão expirada." }, { status: 401 });
  }

  if (session.expiresAt < Date.now() + 30_000) {
    try {
      if (!(await refreshSession(session))) {
        session.destroy();
        return NextResponse.json({ error: "Sessão expirada." }, { status: 401 });
      }
      await session.save();
    } catch {
      session.destroy();
      return NextResponse.json({ error: "Sessão expirada." }, { status: 401 });
    }
  }

  const apiUrl = new URL(process.env.BACKEND_API_URL ?? "http://localhost:8080");
  apiUrl.pathname = requestPath;
  apiUrl.search = new URL(request.url).search;
  const headers = new Headers();
  const contentType = request.headers.get("content-type");
  const accept = request.headers.get("accept");
  if (contentType) headers.set("content-type", contentType);
  if (accept) headers.set("accept", accept);
  headers.set("authorization", `Bearer ${session.accessToken}`);

  try {
    const response = await fetch(apiUrl, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
      cache: "no-store",
    });
    const responseHeaders = new Headers({ "Cache-Control": "no-store" });
    const responseType = response.headers.get("content-type");
    if (responseType) responseHeaders.set("content-type", responseType);
    const buffering = response.headers.get("x-accel-buffering");
    if (buffering) responseHeaders.set("x-accel-buffering", buffering);
    return new Response(response.body, { status: response.status, headers: responseHeaders });
  } catch {
    return NextResponse.json({ error: "Não foi possível acessar a API." }, { status: 502 });
  }
}

export async function GET(request: Request, context: RouteContext) { return proxy(request, context); }
export async function POST(request: Request, context: RouteContext) { return proxy(request, context); }
export async function PATCH(request: Request, context: RouteContext) { return proxy(request, context); }
export async function DELETE(request: Request, context: RouteContext) { return proxy(request, context); }
