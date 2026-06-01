import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { demoCredentials, loginWithCredentials } from '../../services/authService';
import { clearStoredSession, readStoredSession, writeStoredSession } from '../../shared/api';
import { isJwtUsable } from './jwt-utils';
import type { AuthSession, AuthUser, UserRole } from './model';

type AuthContextValue = {
  session: AuthSession | null;
  user: AuthUser | null;
  roles: UserRole[];
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
};

const USER_KEY = 'gea.user.v1';
const AuthContext = createContext<AuthContextValue | null>(null);

function readInitialSession(): AuthSession | null {
  const stored = readStoredSession();
  const rawUser = sessionStorage.getItem(USER_KEY);
  if (!stored || !rawUser) return null;

  try {
    if (!isJwtUsable(stored.token)) {
      clearStoredSession();
      sessionStorage.removeItem(USER_KEY);
      localStorage.removeItem(USER_KEY);
      return null;
    }

    return {
      token: stored.token,
      expiresAt: stored.expiresAt,
      user: JSON.parse(rawUser) as AuthUser,
    };
  } catch {
    clearStoredSession();
    sessionStorage.removeItem(USER_KEY);
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readInitialSession());

  const login = useCallback(async (email: string, password: string) => {
    const nextSession = await loginWithCredentials(email, password);
    writeStoredSession({ token: nextSession.token, expiresAt: nextSession.expiresAt });
    sessionStorage.setItem(USER_KEY, JSON.stringify(nextSession.user));
    setSession(nextSession);
  }, []);

  const logout = useCallback(() => {
    clearStoredSession();
    sessionStorage.removeItem(USER_KEY);
    localStorage.removeItem(USER_KEY);
    setSession(null);
  }, []);

  const value = useMemo(() => ({
    session,
    user: session?.user ?? null,
    roles: session?.user.roles ?? [],
    isAuthenticated: Boolean(session),
    login,
    logout,
  }), [session, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return context;
}

export { demoCredentials };
