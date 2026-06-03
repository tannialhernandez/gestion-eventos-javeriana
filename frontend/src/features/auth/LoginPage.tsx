import React, { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../../shared/ui';
import { demoCredentials, useAuth } from './AuthContext';
import type { DemoCredential } from './model';

function LoginLogo() {
  return (
    <div className="login-logo-card" role="img" aria-label="Pontificia Universidad Javeriana">
      <img src="/javeriana_logo.png" alt="" aria-hidden="true" />
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

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
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
              <p>Use las credenciales institucionales que le fueron asignadas. La integración con Azure AD Javeriana está disponible en Fase 2.</p>
            </div>
          </div>

          <div className="user-options" role="radiogroup" aria-label="Usuarios del sistema">
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

            <p className="form-helper">Seleccione el usuario institucional e ingrese su contraseña.</p>

            {error && <div className="alert alert--error" role="alert">{error}</div>}

            <button className="button button--primary button--wide" type="submit" disabled={isSubmitting}>
              <Icon name="check" />
              {isSubmitting ? 'Validando credenciales' : 'Iniciar sesión'}
            </button>
          </form>

          <footer className="login-footer">
            <img src="/javeriana_logo.png" alt="" aria-hidden="true" />
            <span>Pontificia Universidad Javeriana · Sede Bogotá</span>
            <nav aria-label="Enlaces institucionales de acceso">
              <a href="https://www.javeriana.edu.co/aviso-legal" target="_blank" rel="noopener noreferrer">Aviso legal</a>
              <a href="https://www.javeriana.edu.co/proteccion-datos" target="_blank" rel="noopener noreferrer">Protección de datos (Ley 1581)</a>
              <a href="mailto:soporte.eventos@javeriana.edu.co">Soporte técnico</a>
            </nav>
          </footer>
        </div>
      </section>
    </main>
  );
}
