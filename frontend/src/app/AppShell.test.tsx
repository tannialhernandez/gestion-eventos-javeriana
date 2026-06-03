import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { seedAuthSession } from '../test/auth-session';
import { renderWithProviders, screen } from '../test/test-utils';
import { AppShell } from './AppShell';

function ShellRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/catalogo" element={<h1>Catálogo privado</h1>} />
      </Route>
      <Route path="/login" element={<h1>Login</h1>} />
    </Routes>
  );
}

describe('AppShell', () => {
  it('debe mostrar header institucional Javeriana reforzado', () => {
    seedAuthSession({ role: 'PARTICIPANTE', name: 'Diego Participante' });

    renderWithProviders(<ShellRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(screen.getByRole('img', { name: /pontificia universidad javeriana/i })).toBeInTheDocument();
    expect(screen.getAllByText('Pontificia Universidad Javeriana').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Plataforma de Gestión de Eventos Académicos')).toBeInTheDocument();
    expect(screen.getByText('Diego Participante')).toBeInTheDocument();
  });

  it.each([
    ['ADMIN', 'Administrador'],
    ['ORGANIZADOR', 'Organizador'],
    ['PARTICIPANTE', 'Participante'],
    ['SERVICE', 'Servicio'],
  ] as const)('debe mostrar badge de rol %s como %s', (role, label) => {
    seedAuthSession({ role });

    renderWithProviders(<ShellRoutes />, { routerProps: { initialEntries: ['/catalogo'] } });

    expect(screen.getByLabelText(`Rol de usuario: ${label}`)).toHaveTextContent(label);
  });
});
