import {
  authorizationCodeGrant,
  buildAuthorizationUrl,
  buildEndSessionUrl,
  calculatePKCECodeChallenge,
  customFetch,
  discovery,
  randomNonce,
  randomPKCECodeVerifier,
  randomState,
  refreshTokenGrant,
} from "openid-client";

let configPromise: ReturnType<typeof discovery> | undefined;

export type UserSession = {
  accessToken?: string;
  refreshToken?: string;
  idToken?: string;
  expiresAt?: number;
  email?: string;
  name?: string;
  tenantId?: string;
  codeVerifier?: string;
  state?: string;
  nonce?: string;
};

export function getBaseUrl(request: Request): URL {
  return new URL(process.env.APP_BASE_URL ?? new URL(request.url).origin);
}

export async function getOidcConfig() {
  if (!configPromise) {
    const issuer = new URL(process.env.OIDC_ISSUER_URI ?? "http://localhost:8081/realms/audit");
    const internalIssuer = process.env.OIDC_INTERNAL_ISSUER_URI;
    configPromise = discovery(
      issuer,
      process.env.OIDC_CLIENT_ID ?? "audit-console",
      undefined,
      undefined,
      internalIssuer
        ? {
            [customFetch]: (url, options) => {
              const target = new URL(url);
              if (target.origin === issuer.origin) {
                const internal = new URL(internalIssuer);
                target.host = internal.host;
                target.protocol = internal.protocol;
              }
              return fetch(target, options as RequestInit);
            },
          }
        : undefined,
    );
  }

  return configPromise;
}

export async function getLoginUrl(request: Request, session: UserSession): Promise<URL> {
  const config = await getOidcConfig();
  session.codeVerifier = randomPKCECodeVerifier();
  session.state = randomState();
  session.nonce = randomNonce();
  const codeChallenge = await calculatePKCECodeChallenge(session.codeVerifier);
  const callbackUrl = new URL("/api/auth/callback", getBaseUrl(request));

  return buildAuthorizationUrl(config, {
    redirect_uri: callbackUrl.toString(),
    response_type: "code",
    scope: "openid profile email",
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
    state: session.state,
    nonce: session.nonce,
  });
}

export async function finishLogin(request: Request, session: UserSession): Promise<void> {
  if (!session.codeVerifier || !session.state || !session.nonce) {
    throw new Error("Sessão de autenticação inválida.");
  }

  const config = await getOidcConfig();
  const callbackUrl = new URL("/api/auth/callback", getBaseUrl(request));
  callbackUrl.search = new URL(request.url).search;
  const tokens = await authorizationCodeGrant(config, callbackUrl, {
    pkceCodeVerifier: session.codeVerifier,
    expectedState: session.state,
    expectedNonce: session.nonce,
  });
  const claims = tokens.claims();
  if (!tokens.access_token || !claims) {
    throw new Error("A conta não possui um token OIDC válido.");
  }
  const tenantId = claims.tenant_id;

  if (typeof tenantId !== "string"
      || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(tenantId)) {
    throw new Error("A conta não possui um tenant autorizado.");
  }

  session.accessToken = tokens.access_token;
  session.refreshToken = tokens.refresh_token;
  session.idToken = tokens.id_token;
  session.expiresAt = Date.now() + (tokens.expires_in ?? 300) * 1000;
  session.tenantId = tenantId;
  session.email = typeof claims.email === "string" ? claims.email : undefined;
  session.name = typeof claims.name === "string" ? claims.name : undefined;
  delete session.codeVerifier;
  delete session.state;
  delete session.nonce;
}

export async function refreshSession(session: UserSession): Promise<boolean> {
  if (!session.refreshToken) return false;

  const config = await getOidcConfig();
  const tokens = await refreshTokenGrant(config, session.refreshToken);
  if (!tokens.access_token) return false;

  session.accessToken = tokens.access_token;
  session.refreshToken = tokens.refresh_token ?? session.refreshToken;
  session.expiresAt = Date.now() + (tokens.expires_in ?? 300) * 1000;
  return true;
}

export async function getLogoutUrl(request: Request, session: UserSession): Promise<URL | null> {
  if (!session.idToken) return null;

  const config = await getOidcConfig();
  return buildEndSessionUrl(config, {
    id_token_hint: session.idToken,
    post_logout_redirect_uri: new URL("/login", getBaseUrl(request)).toString(),
  });
}
