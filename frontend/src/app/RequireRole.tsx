import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth';
import type { UserRole } from '../features/auth/model';

type RequireRoleProps = {
  allowedRoles: UserRole[];
  children: ReactNode;
};

export function RequireRole({ allowedRoles, children }: RequireRoleProps) {
  const { roles } = useAuth();
  const location = useLocation();
  const isAllowed = roles.some((role) => allowedRoles.includes(role));

  if (!isAllowed) {
    return <Navigate to="/catalogo" replace state={{ deniedFrom: location.pathname, accessMessage: 'Acceso restringido' }} />;
  }

  return <>{children}</>;
}
