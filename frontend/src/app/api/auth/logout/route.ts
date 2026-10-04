import { NextResponse } from "next/server";
import { getBaseUrl, getLogoutUrl } from "@/lib/oidc";
import { getSession } from "@/lib/session";

export async function POST(request: Request) {
  const baseUrl = getBaseUrl(request);
  if (request.headers.get("origin") !== baseUrl.origin) {
    return NextResponse.json({ error: "Origem inválida." }, { status: 403 });
  }

  const session = await getSession();
  let logoutUrl: URL | null = null;
  try {
    logoutUrl = await getLogoutUrl(request, session);
  } catch {
    logoutUrl = null;
  }
  session.destroy();

  return NextResponse.json({ redirectTo: logoutUrl?.toString() ?? "/login" }, {
    headers: { "Cache-Control": "no-store" },
  });
}
