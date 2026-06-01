import { describe, expect, it } from 'vitest';
import { extractRoles, isExpired, parseJwt } from './jwt-utils';

function tokenWithClaims(claims: Record<string, unknown>) {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value))
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');

  return `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(claims)}.signature`;
}

describe('JWT Claims Validation', () => {
  it('extrae roles del claim "roles"', () => {
    const token = tokenWithClaims({
      sub: 'user-demo',
      roles: ['PARTICIPANTE'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    expect(extractRoles(token)).toEqual(['PARTICIPANTE']);
  });

  it('detecta token expirado', () => {
    const tokenExpirado = tokenWithClaims({
      sub: 'user-demo',
      roles: ['PARTICIPANTE'],
      exp: Math.floor(Date.now() / 1000) - 60,
    });

    expect(isExpired(tokenExpirado)).toBe(true);
  });

  it('detecta token valido', () => {
    const tokenValido = tokenWithClaims({
      sub: 'user-demo',
      roles: ['PARTICIPANTE'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    expect(isExpired(tokenValido)).toBe(false);
  });

  it('rechaza token malformado', () => {
    expect(() => parseJwt('not-a-jwt')).toThrow(/JWT malformado/);
  });
});
