import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { SESSION_EXPIRED_EVENT, clearToken, getToken, setToken } from '../api/client';
import { authApi } from '../api/endpoints';
import type { UserResponse } from '../api/types';

interface AuthContextValue {
  user: UserResponse | null;
  initialising: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Session state for the whole app.
 *
 * On mount it validates a stored token by calling `/api/auth/me`. That round trip is deliberate:
 * a token in localStorage proves nothing, and this is what lets the app drop a stale session
 * before any protected screen renders. A 401 from any later call also clears the session, via the
 * event the API client emits.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initialising, setInitialising] = useState<boolean>(true);

  const refreshUser = useCallback(async () => {
    const profile = await authApi.me();
    setUser(profile);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const restore = async () => {
      if (!getToken()) {
        setInitialising(false);
        return;
      }
      try {
        const profile = await authApi.me();
        if (!cancelled) {
          setUser(profile);
        }
      } catch {
        clearToken();
      } finally {
        if (!cancelled) {
          setInitialising(false);
        }
      }
    };
    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onExpired = () => setUser(null);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login({ email, password });
    setToken(response.accessToken);
    setUser(response.user);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const response = await authApi.register({ email, password, displayName });
    setToken(response.accessToken);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, initialising, login, register, logout, refreshUser }),
    [user, initialising, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
