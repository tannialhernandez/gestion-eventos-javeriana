import type { UserRole } from './model';

type RoleTone = 'admin' | 'organizador' | 'participante' | 'service';

type RolePresentation = {
  label: string;
  tone: RoleTone;
  catalogMessage: string;
};

const rolePriority: UserRole[] = ['ADMIN', 'ORGANIZADOR', 'SERVICE', 'PARTICIPANTE'];

const rolePresentation: Record<UserRole, RolePresentation> = {
  ADMIN: {
    label: 'Administrador',
    tone: 'admin',
    catalogMessage: 'Vista de Administración — Gestión disponible en Fase 2',
  },
  ORGANIZADOR: {
    label: 'Organizador',
    tone: 'organizador',
    catalogMessage: 'Vista de Organizador — Creación de eventos disponible en Fase 2',
  },
  PARTICIPANTE: {
    label: 'Participante',
    tone: 'participante',
    catalogMessage: 'Explora eventos académicos y completa tu inscripción',
  },
  SERVICE: {
    label: 'Servicio',
    tone: 'service',
    catalogMessage: 'Vista de Servicio — Operación técnica disponible en Fase 2',
  },
};

export function getPrimaryRole(roles: UserRole[] | undefined): UserRole {
  return rolePriority.find((role) => roles?.includes(role)) ?? 'PARTICIPANTE';
}

export function getRolePresentation(roles: UserRole[] | undefined): RolePresentation {
  return rolePresentation[getPrimaryRole(roles)];
}
