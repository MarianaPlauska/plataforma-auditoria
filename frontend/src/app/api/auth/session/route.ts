import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { refreshSession } from "@/lib/oidc";

export async function GET() {
  const session = await getSession();
  if (session.expiresAt && session.expiresAt < Date.now() + 30_000) {
    try {
      if (await refreshSession(session)) {
        await session.save();
      } else {
        session.destroy();
      }
    } catch {
      session.destroy();
    }
  }

  if (!session.accessToken || !session.tenantId || !session.expiresAt || session.expiresAt <= Date.now()) {
    session.destroy();
    return NextResponse.json({ authenticated: false }, { headers: { "Cache-Control": "no-store" } });
  }

  return NextResponse.json({
    authenticated: true,
    email: session.email,
    name: session.name,
    tenantId: session.tenantId,
  }, { headers: { "Cache-Control": "no-store" } });
}
