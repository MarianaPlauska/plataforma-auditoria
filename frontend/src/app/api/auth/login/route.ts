import { NextResponse } from "next/server";

type TokenResponse = {
  access_token?: string;
  expires_in?: number;
  error?: string;
  error_description?: string;
};

export async function POST(request: Request) {
  const body = await request.json() as { email?: string; password?: string };
  const email = body.email?.trim();
  const password = body.password;

  if (!email || !password) {
    return NextResponse.json({ error: "Informe e-mail e senha." }, { status: 400 });
  }

  const issuer = process.env.OIDC_ISSUER_URI ?? "http://localhost:8081/realms/audit";
  const clientId = process.env.OIDC_CLIENT_ID ?? "audit-console";

  try {
    const response = await fetch(`${issuer}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "password",
        client_id: clientId,
        username: email,
        password,
      }),
    });

    const data = await response.json() as TokenResponse;
    if (!response.ok || !data.access_token) {
      const message = data.error_description ?? data.error ?? "Credenciais inválidas.";
      return NextResponse.json({ error: message }, { status: 401 });
    }

    return NextResponse.json({
      accessToken: data.access_token,
      expiresIn: data.expires_in ?? 300,
    });
  } catch {
    return NextResponse.json(
      { error: "Não foi possível contactar o servidor de autenticação. Verifique se o Keycloak está ativo." },
      { status: 503 },
    );
  }
}
