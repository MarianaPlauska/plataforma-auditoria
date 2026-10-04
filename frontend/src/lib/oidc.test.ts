import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  authorizationCodeGrant: vi.fn(),
  buildAuthorizationUrl: vi.fn(),
  buildEndSessionUrl: vi.fn(),
  calculatePKCECodeChallenge: vi.fn(),
  discovery: vi.fn(),
  randomNonce: vi.fn(),
  randomPKCECodeVerifier: vi.fn(),
  randomState: vi.fn(),
  refreshTokenGrant: vi.fn(),
}));

vi.mock("openid-client", () => ({
  ...mocks,
  customFetch: Symbol.for("customFetch"),
}));

import { finishLogin, getLoginUrl, type UserSession } from "@/lib/oidc";

describe("OIDC com PKCE", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.discovery.mockResolvedValue({});
    mocks.randomPKCECodeVerifier.mockReturnValue("verificador-seguro");
    mocks.randomState.mockReturnValue("estado-aleatorio");
    mocks.randomNonce.mockReturnValue("nonce-aleatorio");
    mocks.calculatePKCECodeChallenge.mockResolvedValue("desafio-pkce");
    mocks.buildAuthorizationUrl.mockReturnValue(new URL("http://localhost:8081/authorize"));
  });

  it("gera autorização com state, nonce e PKCE S256", async () => {
    const session: UserSession = {};
    const url = await getLoginUrl(new Request("http://localhost:3000/api/auth/login"), session);
    const parameters = mocks.buildAuthorizationUrl.mock.calls[0][1] as Record<string, string>;

    expect(url.toString()).toContain("localhost:8081");
    expect(parameters.state).toBe("estado-aleatorio");
    expect(parameters.nonce).toBe("nonce-aleatorio");
    expect(parameters.code_challenge).toBe("desafio-pkce");
    expect(parameters.code_challenge_method).toBe("S256");
    expect(parameters.redirect_uri).toBe("http://localhost:3000/api/auth/callback");
  });

  it("valida o retorno OIDC e cria uma sessão sem expor o token no cliente", async () => {
    const session: UserSession = {
      codeVerifier: "verificador-seguro",
      state: "estado-aleatorio",
      nonce: "nonce-aleatorio",
    };
    mocks.authorizationCodeGrant.mockResolvedValue({
      access_token: "token-de-acesso",
      refresh_token: "token-de-renovacao",
      id_token: "token-de-identidade",
      expires_in: 300,
      claims: () => ({
        tenant_id: "00000000-0000-0000-0000-000000000001",
        email: "auditor@local.demo",
        name: "Auditor",
      }),
    });

    await finishLogin(new Request("http://localhost:3000/api/auth/callback?code=codigo&state=estado-aleatorio"), session);

    expect(mocks.authorizationCodeGrant.mock.calls[0][2]).toMatchObject({
      pkceCodeVerifier: "verificador-seguro",
      expectedState: "estado-aleatorio",
      expectedNonce: "nonce-aleatorio",
    });
    expect(session.accessToken).toBe("token-de-acesso");
    expect(session.tenantId).toBe("00000000-0000-0000-0000-000000000001");
    expect(session.codeVerifier).toBeUndefined();
  });

  it("recusa criar sessão sem tenant UUID", async () => {
    const session: UserSession = {
      codeVerifier: "verificador-seguro",
      state: "estado-aleatorio",
      nonce: "nonce-aleatorio",
    };
    mocks.authorizationCodeGrant.mockResolvedValue({
      access_token: "token-de-acesso",
      claims: () => ({ tenant_id: "tenant-invalido" }),
    });

    await expect(finishLogin(new Request("http://localhost:3000/api/auth/callback?code=codigo&state=estado-aleatorio"), session))
      .rejects.toThrow("tenant autorizado");
  });
});
