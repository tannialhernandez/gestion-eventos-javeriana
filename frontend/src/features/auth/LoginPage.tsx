import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../../shared/ui';
import { demoCredentials, useAuth } from './AuthContext';
import type { DemoCredential } from './model';

function LoginLogo() {
  return (
    <div className="login-logo-card" role="img" aria-label="Pontificia Universidad Javeriana">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 96" aria-hidden="true" focusable="false">
        <rect width="240" height="96" rx="10" fill="#003c71" />
        <path d="M47 18h34l-17 20z" fill="#FFC72C" />
        <path d="M64 38l19 40H45z" fill="#FFC72C" />
        <path d="M64 44l9 22H55z" fill="#003c71" />
        <text x="102" y="40" fill="#FFFFFF" fontFamily="Arial, Helvetica, sans-serif" fontSize="16" fontWeight="700">Pontificia</text>
        <text x="102" y="59" fill="#FFFFFF" fontFamily="Arial, Helvetica, sans-serif" fontSize="16" fontWeight="700">Universidad</text>
        <text x="102" y="78" fill="#FFC72C" fontFamily="Arial, Helvetica, sans-serif" fontSize="16" fontWeight="700">Javeriana</text>
      </svg>
    </div>
  );
}

export function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [selectedCredential, setSelectedCredential] = useState<DemoCredential>(demoCredentials[0]);
  const [email, setEmail] = useState(selectedCredential.email);
  const [password, setPassword] = useState(selectedCredential.password);
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (session) {
    return <Navigate to="/catalogo" replace />;
  }

  const selectCredential = (credential: DemoCredential) => {
    setSelectedCredential(credential);
    setEmail(credential.email);
    setPassword(credential.password);
    setError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email.trim(), password);
      const from = (location.state as { from?: string } | null)?.from ?? '/catalogo';
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesion.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-panel__form">
          <header className="login-branding">
            <LoginLogo />
            <span>Acceso institucional</span>
            <h1 id="login-title">Plataforma de Gestión de Eventos Académicos</h1>
            <p>Pontificia Universidad Javeriana</p>
          </header>

          <div className="section-heading">
            <span className="section-heading__icon">
              <Icon name="shield" />
            </span>
            <div>
              <h2>Acceso institucional</h2>
              <p>Los usuarios provienen de Azure AD Javeriana (Fase 2). Para demo, usar credenciales asignadas.</p>
            </div>
          </div>

          <div className="user-options" role="radiogroup" aria-label="Usuarios demo">
            {demoCredentials.map((credential) => (
              <button
                className={`user-option ${selectedCredential.email === credential.email ? 'user-option--selected' : ''}`}
                key={credential.email}
                type="button"
                onClick={() => selectCredential(credential)}
                role="radio"
                aria-checked={selectedCredential.email === credential.email}
              >
                <span>{credential.name}</span>
                <small>{credential.role}</small>
              </button>
            ))}
          </div>

          <form className="login-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>Email</span>
              <input
                className="input"
                name="email"
                type="email"
                autoComplete="username"
                placeholder="usuario@javeriana.edu.co"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </label>

            <label className="field">
              <span>Contraseña</span>
              <input
                className="input"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={6}
                required
              />
            </label>

            <p className="form-helper">Credenciales demo: cualquier usuario listado usa contraseña <strong>demo123</strong>.</p>

            {error && <div className="alert alert--error" role="alert">{error}</div>}

            <button className="button button--primary button--wide" type="submit" disabled={isSubmitting}>
              <Icon name="check" />
              {isSubmitting ? 'Validando credenciales' : 'Iniciar sesión'}
            </button>
          </form>

          <footer className="login-footer">
            <img src="/javeriana-logo.svg" alt="" aria-hidden="true" />
            <span>© 2026 Pontificia Universidad Javeriana - Sede Bogotá</span>
            <nav aria-label="Enlaces institucionales de acceso">
              <a href="#terminos">Términos</a>
              <a href="#privacidad">Privacidad (Ley 1581)</a>
              <a href="mailto:ti@javeriana.edu.co">Contacto TI</a>
            </nav>
          </footer>
        </div>
      </section>
    </main>
  );
}
