"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthSession } from "@/lib/auth";

type AuthContextValue = {
  ready: boolean;
  session: AuthSession | null;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    let active = true;
    fetch("/api/auth/session", { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) return null;
        const data = await response.json() as AuthSession & { authenticated: boolean };
        return data.authenticated ? data : null;
      })
      .then((data) => {
        if (active) setSession(data);
      })
      .catch(() => {
        if (active) setSession(null);
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => { active = false; };
  }, []);

  const logout = useCallback(async () => {
    setSession(null);
    try {
      const response = await fetch("/api/auth/logout", { method: "POST" });
      if (!response.ok) {
        window.location.assign("/login");
        return;
      }
      const data = await response.json() as { redirectTo?: string };
      window.location.assign(data.redirectTo ?? "/login");
    } catch {
      window.location.assign("/login");
    }
  }, []);

  const value = useMemo(() => ({ ready, session, logout }), [ready, session, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return context;
}
