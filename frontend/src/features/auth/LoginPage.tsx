import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../../shared/ui';
import { demoCredentials, useAuth } from './AuthContext';
import type { DemoCredential } from './model';

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
            <img src="/javeriana-logo.svg" alt="Pontificia Universidad Javeriana" />
            <span>Entrega 3</span>
            <h1 id="login-title">Plataforma de Gestión de Eventos Académicos</h1>
            <p>Pontificia Universidad Javeriana</p>
          </header>

          <div className="section-heading">
            <span className="section-heading__icon">
              <Icon name="shield" />
            </span>
            <div>
              <h2>Ingreso institucional</h2>
              <p>Autenticación contra auth-service-stub con JWT RS256.</p>
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

          <footer className="login-footer">© 2026 Pontificia Universidad Javeriana | Plataforma de Eventos</footer>
        </div>
      </section>
    </main>
  );
}
