export type UserRole = 'PARTICIPANTE' | 'ORGANIZADOR' | 'ADMIN' | 'SERVICE';

export type AuthUser = {
  id: string;
  name: string;
  email: string;
  roles: UserRole[];
};

export type DemoCredential = {
  name: string;
  email: string;
  password: string;
  role: UserRole;
};

export type AuthSession = {
  user: AuthUser;
  token: string;
  expiresAt: string;
};
