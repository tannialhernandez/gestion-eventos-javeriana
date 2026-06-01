import axios, { type AxiosInstance } from 'axios';
import { attachAxiosErrorInterceptor } from '../../lib/api';
import { isAppError, normalizeAppError } from '../../lib/errors';

const TOKEN_KEY = 'gea.session.v1';

type StoredSession = {
  token: string;
  expiresAt: string;
};

export type ApiError = {
  status: number;
  code: string;
  message: string;
  retryAfterSeconds?: number;
};

export function createHttpClient(baseURL: string, serviceName: string): AxiosInstance {
  const client = axios.create({
    baseURL,
    timeout: 5000,
    headers: { 'Content-Type': 'application/json' },
  });

  client.interceptors.request.use((config) => {
    const session = readStoredSession();
    if (session) {
      config.headers.Authorization = `Bearer ${session.token}`;
    }
    config.headers['X-Correlation-Id'] = crypto.randomUUID();
    return config;
  });

  attachAxiosErrorInterceptor(client, serviceName);

  return client;
}

export function readStoredSession(): StoredSession | null {
  const raw = sessionStorage.getItem(TOKEN_KEY);
  if (!raw) return null;

  try {
    const session = JSON.parse(raw) as StoredSession;
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      sessionStorage.removeItem(TOKEN_KEY);
      return null;
    }
    return session;
  } catch {
    sessionStorage.removeItem(TOKEN_KEY);
    return null;
  }
}

export function writeStoredSession(session: StoredSession): void {
  sessionStorage.setItem(TOKEN_KEY, JSON.stringify(session));
}

export function clearStoredSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);
}

export function toApiError(error: unknown): ApiError {
  if (isAppError(error)) {
    return {
      status: error.status,
      code: error.code,
      message: error.message,
      retryAfterSeconds: error.retryAfterSeconds,
    };
  }

  const normalized = normalizeAppError(error);
  return {
    status: normalized.status,
    code: normalized.code,
    message: normalized.message,
    retryAfterSeconds: normalized.retryAfterSeconds,
  };
}
