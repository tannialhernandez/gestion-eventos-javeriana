import { createHttpClient } from '../shared/api';
import { env } from '../shared/config/env';
import type { AuthSession, AuthUser, DemoCredential, UserRole } from '../features/auth/model';

type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: {
    id: string;
    nombre: string;
    email: string;
    roles: UserRole[];
  };
};

type JwtClaims = {
  sub?: string;
  email?: string;
  name?: string;
  roles?: UserRole[];
};

const authHttp = createHttpClient(env.authApiUrl, 'auth-service');

export const demoCredentials: DemoCredential[] = [
  {
    name: 'Laura García',
    email: 'laura.participante@javeriana.edu.co',
    password: 'demo123',
    role: 'PARTICIPANTE',
  },
  {
    name: 'Diego Rodríguez',
    email: 'diego.participante@javeriana.edu.co',
    password: 'demo123',
    role: 'PARTICIPANTE',
  },
  {
    name: 'Dr. Carlos Mejía',
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
    role: 'ORGANIZADOR',
  },
  {
    name: 'Ana María Torres',
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
    role: 'ADMIN',
  },
  {
    name: 'Sofía Vargas',
    email: 'sofia.soporte@javeriana.edu.co',
    password: 'demo123',
    role: 'PARTICIPANTE',
  },
];

function decodeJwtPayload(token: string): JwtClaims {
  const [, payload] = token.split('.');
  if (!payload) return {};
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
  return JSON.parse(atob(padded)) as JwtClaims;
}

function userFromResponse(response: LoginResponse): AuthUser {
  const claims = decodeJwtPayload(response.accessToken);
  return {
    id: claims.sub ?? response.user.id,
    name: claims.name ?? response.user.nombre,
    email: claims.email ?? response.user.email,
    roles: claims.roles ?? response.user.roles,
  };
}

export async function loginWithCredentials(email: string, password: string): Promise<AuthSession> {
  const { data } = await authHttp.post<LoginResponse>('/auth/login', { email, password });

  return {
    token: data.accessToken,
    expiresAt: data.expiresAt,
    user: userFromResponse(data),
  };
}
