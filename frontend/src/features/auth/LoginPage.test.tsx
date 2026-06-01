import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { LoginPage } from './LoginPage';

function LoginRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/catalogo" element={<h1>Catalogo protegido</h1>} />
    </Routes>
  );
}

describe('LoginPage', () => {
  it('debe renderizar branding Javeriana', () => {
    renderWithProviders(<LoginRoutes />, { routerProps: { initialEntries: ['/login'] } });

    expect(screen.getByRole('img', { name: /pontificia universidad javeriana/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /plataforma de gestión de eventos académicos/i })).toBeInTheDocument();
    expect(screen.getByText('Pontificia Universidad Javeriana')).toBeInTheDocument();
    expect(screen.getByText(/© 2026 Pontificia Universidad Javeriana/i)).toBeInTheDocument();
  });

  it('debe validar campos obligatorios', () => {
    renderWithProviders(<LoginRoutes />, { routerProps: { initialEntries: ['/login'] } });

    expect(screen.getByLabelText(/email/i)).toBeRequired();
    expect(screen.getByLabelText(/contraseña/i)).toBeRequired();
  });

  it('debe llamar a login y redirigir a catalogo tras submit exitoso', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginRoutes />, { routerProps: { initialEntries: ['/login'] } });

    await user.clear(screen.getByLabelText(/email/i));
    await user.type(screen.getByLabelText(/email/i), 'diego.participante@javeriana.edu.co');
    await user.clear(screen.getByLabelText(/contraseña/i));
    await user.type(screen.getByLabelText(/contraseña/i), 'demo123');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /catalogo protegido/i })).toBeInTheDocument());
  });

  it('debe mostrar error con credenciales invalidas', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginRoutes />, { routerProps: { initialEntries: ['/login'] } });

    await user.clear(screen.getByLabelText(/email/i));
    await user.type(screen.getByLabelText(/email/i), 'falso@javeriana.edu.co');
    await user.clear(screen.getByLabelText(/contraseña/i));
    await user.type(screen.getByLabelText(/contraseña/i), 'wrong123');
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }));

    expect(await screen.findByText(/credenciales invalidas/i)).toBeInTheDocument();
  });
});
