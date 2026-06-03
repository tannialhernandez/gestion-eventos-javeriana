import type { UserRole } from '../features/auth/model';

type SeedAuthSessionOptions = {
  role?: UserRole;
  name?: string;
  email?: string;
};

function base64UrlJson(value: unknown): string {
  return btoa(JSON.stringify(value))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

export function createTestJwt(role: UserRole = 'PARTICIPANTE'): string {
  const header = base64UrlJson({ alg: 'RS256', typ: 'JWT', kid: 'test-key' });
  const payload = base64UrlJson({
    sub: `test-${role.toLowerCase()}`,
    email: `${role.toLowerCase()}@javeriana.edu.co`,
    name: `Usuario ${role}`,
    roles: [role],
    exp: 1_999_999_999,
  });
  return `${header}.${payload}.test-signature`;
}

export function seedAuthSession({
  role = 'PARTICIPANTE',
  name = `Usuario ${role}`,
  email = `${role.toLowerCase()}@javeriana.edu.co`,
}: SeedAuthSessionOptions = {}): void {
  sessionStorage.setItem('gea.session.v1', JSON.stringify({
    token: createTestJwt(role),
    expiresAt: '2033-05-31T12:00:00Z',
  }));
  sessionStorage.setItem('gea.user.v1', JSON.stringify({
    id: `test-${role.toLowerCase()}`,
    name,
    email,
    roles: [role],
  }));
}
