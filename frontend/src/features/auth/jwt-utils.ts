import type { UserRole } from './model';

export type JwtClaims = {
  sub?: string;
  email?: string;
  name?: string;
  roles?: unknown;
  exp?: unknown;
  [claim: string]: unknown;
};

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');

  if (typeof atob === 'function') {
    return atob(padded);
  }

  return Buffer.from(padded, 'base64').toString('utf8');
}

function isUserRole(value: unknown): value is UserRole {
  return value === 'PARTICIPANTE' || value === 'ORGANIZADOR' || value === 'ADMIN';
}

export function parseJwt(token: string): JwtClaims {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) {
    throw new Error('JWT malformado');
  }

  try {
    return JSON.parse(decodeBase64Url(parts[1])) as JwtClaims;
  } catch (error) {
    throw new Error('JWT malformado', { cause: error });
  }
}

export function extractRoles(token: string): UserRole[] {
  const claims = parseJwt(token);
  if (!Array.isArray(claims.roles)) return [];
  return claims.roles.filter(isUserRole);
}

export function isExpired(token: string, nowMs = Date.now()): boolean {
  const claims = parseJwt(token);
  if (typeof claims.exp !== 'number') return true;
  return claims.exp * 1000 <= nowMs;
}

export function isJwtUsable(token: string): boolean {
  try {
    return !isExpired(token);
  } catch {
    return false;
  }
}
