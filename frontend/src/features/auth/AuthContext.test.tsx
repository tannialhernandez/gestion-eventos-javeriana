import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { mockJwt } from '../../test/mocks/handlers';
import { useAuth } from './AuthContext';

function Probe() {
  const { isAuthenticated, user, roles, login, logout } = useAuth();
  return (
    <div>
      <output data-testid="status">{isAuthenticated ? 'auth' : 'no-auth'}</output>
      <output data-testid="email">{user?.email ?? 'none'}</output>
      <output data-testid="roles">{roles.join(',') || 'none'}</output>
      <button type="button" onClick={() => login('diego.participante@javeriana.edu.co', 'demo123')}>
        Login
      </button>
      <button type="button" onClick={logout}>
        Logout
      </button>
    </div>
  );
}

describe('AuthContext', () => {
  it('debe iniciar sin usuario autenticado', () => {
    renderWithProviders(<Probe />);

    expect(screen.getByTestId('status')).toHaveTextContent('no-auth');
    expect(screen.getByTestId('email')).toHaveTextContent('none');
  });

  it('debe persistir token en sessionStorage tras login', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Probe />);

    await user.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('auth'));
    expect(screen.getByTestId('email')).toHaveTextContent('diego.participante@javeriana.edu.co');
    expect(sessionStorage.getItem('gea.session.v1')).toContain(mockJwt);
  });

  it('debe restaurar sesion desde sessionStorage al recargar', () => {
    sessionStorage.setItem('gea.session.v1', JSON.stringify({ token: mockJwt, expiresAt: '2033-05-31T12:00:00Z' }));
    sessionStorage.setItem('gea.user.v1', JSON.stringify({
      id: 'user-demo-001',
      name: 'Diego Participante',
      email: 'diego.participante@javeriana.edu.co',
      roles: ['PARTICIPANTE'],
    }));

    renderWithProviders(<Probe />);

    expect(screen.getByTestId('status')).toHaveTextContent('auth');
    expect(screen.getByTestId('email')).toHaveTextContent('diego.participante@javeriana.edu.co');
  });

  it('debe limpiar sessionStorage tras logout', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Probe />);

    await user.click(screen.getByRole('button', { name: 'Login' }));
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('auth'));
    await user.click(screen.getByRole('button', { name: 'Logout' }));

    expect(screen.getByTestId('status')).toHaveTextContent('no-auth');
    expect(sessionStorage.getItem('gea.session.v1')).toBeNull();
    expect(sessionStorage.getItem('gea.user.v1')).toBeNull();
  });

  it('debe exponer roles correctamente', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Probe />);

    await user.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => expect(screen.getByTestId('roles')).toHaveTextContent('PARTICIPANTE'));
  });
});
