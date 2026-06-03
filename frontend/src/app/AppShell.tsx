import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { DegradedServiceBanner, NetworkOfflineBanner } from '../components';
import { useAuth } from '../features/auth';
import { getRolePresentation } from '../features/auth/rolePresentation';
import { Icon } from '../shared/ui';

export function AppShell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const role = getRolePresentation(session?.user.roles);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <img src="/javeriana-logo.svg" alt="Pontificia Universidad Javeriana" className="brand__mark" />
          <div className="brand__text">
            <strong>Pontificia Universidad Javeriana</strong>
            <span>Plataforma de Gestión de Eventos Académicos</span>
          </div>
        </div>

        <nav className="topbar__nav" aria-label="Navegacion principal">
          <NavLink to="/catalogo">Catálogo</NavLink>
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
        <img src="/javeriana-logo.svg" alt="" className="institutional-footer__mark" aria-hidden="true" />
        <p>© 2026 Pontificia Universidad Javeriana - Sede Bogotá</p>
        <nav aria-label="Enlaces institucionales">
          <a href="#terminos">Términos</a>
          <a href="#privacidad">Privacidad (Ley 1581)</a>
          <a href="mailto:ti@javeriana.edu.co">Contacto TI</a>
        </nav>
      </footer>
    </div>
  );
}
