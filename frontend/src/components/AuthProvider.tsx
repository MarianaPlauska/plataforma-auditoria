"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthSession, clearStoredToken, decodeSession, getStoredToken, storeToken } from "@/lib/auth";

type AuthContextValue = {
  ready: boolean;
  session: AuthSession | null;
  login: (accessToken: string) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [session, setSession] = useState<AuthSession | null>(null);

  useEffect(() => {
    const token = getStoredToken();
    setSession(token ? decodeSession(token) : null);
    setReady(true);
  }, []);

  const login = useCallback((accessToken: string) => {
    storeToken(accessToken);
    setSession(decodeSession(accessToken));
  }, []);

  const logout = useCallback(() => {
    clearStoredToken();
    setSession(null);
  }, []);

  const value = useMemo(() => ({ ready, session, login, logout }), [ready, session, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth deve ser usado dentro de AuthProvider");
  return context;
}
