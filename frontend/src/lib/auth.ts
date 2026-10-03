export type AuthSession = {
  accessToken: string;
  email?: string;
  name?: string;
  tenantId?: string;
};

const STORAGE_KEY = "audit-desk.access-token";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem(STORAGE_KEY);
}

export function storeToken(accessToken: string): void {
  sessionStorage.setItem(STORAGE_KEY, accessToken);
}

export function clearStoredToken(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function decodeSession(accessToken: string): AuthSession {
  const payload = JSON.parse(atob(accessToken.split(".")[1] ?? "")) as Record<string, unknown>;
  return {
    accessToken,
    email: typeof payload.email === "string" ? payload.email : typeof payload.preferred_username === "string" ? payload.preferred_username : undefined,
    name: typeof payload.name === "string" ? payload.name : undefined,
    tenantId: typeof payload.tenant_id === "string" ? payload.tenant_id : undefined,
  };
}
