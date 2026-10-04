import { cookies } from "next/headers";
import { getIronSession, type SessionOptions } from "iron-session";
import type { UserSession } from "@/lib/oidc";

const sessionTtl = 60 * 60 * 8;

function getSessionOptions(): SessionOptions {
  const password = process.env.AUTH_SESSION_SECRET;
  if (process.env.NODE_ENV === "production" && (!password || password.length < 32)) {
    throw new Error("AUTH_SESSION_SECRET deve ter pelo menos 32 caracteres em produção.");
  }

  return {
    cookieName: "audit-desk.session",
    password: password ?? "local-only-session-secret-change-this-value",
    ttl: sessionTtl,
    cookieOptions: {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
    },
  };
}

export async function getSession() {
  return getIronSession<UserSession>(await cookies(), getSessionOptions());
}
