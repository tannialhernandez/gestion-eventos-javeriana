import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { DegradedServiceBanner, NetworkOfflineBanner } from '../components';
import { useAuth } from '../features/auth';
import { canCreateEvents, getRolePresentation } from '../features/auth/rolePresentation';
import { Icon } from '../shared/ui';

export function AppShell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const role = getRolePresentation(session?.user.roles);
  const canCreate = canCreateEvents(session?.user.roles);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <img src="/javeriana_logo.png" alt="Pontificia Universidad Javeriana" className="brand__mark" />
          <div className="brand__text">
            <strong>Pontificia Universidad Javeriana</strong>
            <span>Plataforma de Gestión de Eventos Académicos</span>
          </div>
        </div>

        <nav className="topbar__nav" aria-label="Navegacion principal">
          <NavLink to="/catalogo">Catálogo</NavLink>
          {canCreate && <NavLink to="/eventos/nuevo">Crear evento</NavLink>}
        </nav>

        <div className="topbar__session">
          <div className="session-chip">
            <Icon name="user" size={16} />
            <span>{session?.user.name}</span>
          </div>
          <span className={`role-badge role-badge--${role.tone}`} aria-label={`Rol de usuario: ${role.label}`}>
            {role.label}
          </span>
          <button className="icon-button" type="button" onClick={handleLogout} title="Cerrar sesión" aria-label="Cerrar sesión">
            <Icon name="log-out" />
          </button>
        </div>
      </header>
      <NetworkOfflineBanner />
      <DegradedServiceBanner />
      <main className="page-shell">
        <Outlet />
      </main>
      <footer className="institutional-footer" aria-label="Información institucional">
        <div className="institutional-footer__brand">
          <img src="/javeriana_logo.png" alt="" className="institutional-footer__mark" aria-hidden="true" />
          <div className="institutional-footer__info">
            <strong>Pontificia Universidad Javeriana</strong>
            <p>Sede Bogotá · Cra. 7 No. 40-62 · Bogotá D.C., Colombia</p>
            <p>Personería Jurídica - Resolución No. 73 del 12 de diciembre de 1933</p>
          </div>
        </div>
        <nav aria-label="Enlaces institucionales">
          <a href="https://www.javeriana.edu.co/aviso-legal" target="_blank" rel="noopener noreferrer">Aviso legal</a>
          <a href="https://www.javeriana.edu.co/proteccion-datos" target="_blank" rel="noopener noreferrer">Protección de datos (Ley 1581)</a>
          <a href="mailto:soporte.eventos@javeriana.edu.co">Soporte técnico</a>
        </nav>
        <p className="institutional-footer__copy">© 2026 Pontificia Universidad Javeriana. Todos los derechos reservados.</p>
      </footer>
    </div>
  );
}
