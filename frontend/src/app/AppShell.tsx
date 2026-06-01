import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import heroAsset from '../assets/hero.png';
import { DegradedServiceBanner, NetworkOfflineBanner } from '../components';
import { useAuth } from '../features/auth';
import { Icon } from '../shared/ui';

export function AppShell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <img src={heroAsset} alt="" className="brand__mark" />
          <div>
            <strong>Eventos Académicos</strong>
            <span>Pontificia Universidad Javeriana</span>
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
    </div>
  );
}
