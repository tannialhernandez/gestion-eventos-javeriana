import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { RequireRole } from '../../app/RequireRole';
import { CatalogPage } from '../catalog';
import { seedAuthSession } from '../../test/auth-session';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { EventCreatePage } from './EventCreatePage';

function CreateRoutes() {
  return (
    <Routes>
      <Route
        path="/eventos/nuevo"
        element={(
          <RequireRole allowedRoles={['ORGANIZADOR']}>
            <EventCreatePage />
          </RequireRole>
        )}
      />
      <Route path="/eventos/:eventoId" element={<h1>Detalle creado</h1>} />
      <Route path="/catalogo" element={<CatalogPage />} />
    </Routes>
  );
}

async function fillCreateForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/título/i), 'Evento nuevo desde frontend');
  await user.type(screen.getByLabelText(/descripción/i), 'Creación validada con MSW.');
  await user.clear(screen.getByLabelText(/fecha inicio/i));
  await user.type(screen.getByLabelText(/fecha inicio/i), '2033-07-10');
  await user.clear(screen.getByLabelText(/fecha fin/i));
  await user.type(screen.getByLabelText(/fecha fin/i), '2033-07-11');
  await user.clear(screen.getByLabelText(/fecha límite de inscripción/i));
  await user.type(screen.getByLabelText(/fecha límite de inscripción/i), '2033-07-01T23:59');
  await user.selectOptions(screen.getByLabelText(/estado/i), 'PENDIENTE_PUBLICACION');
  return user;
}

describe('EventCreatePage', () => {
  it('crea evento y redirige al detalle', async () => {
    seedAuthSession({ role: 'ORGANIZADOR', id: '33333333-3333-3333-3333-333333333333' });
    renderWithProviders(<CreateRoutes />, { routerProps: { initialEntries: ['/eventos/nuevo'] } });

    expect(screen.getByRole('heading', { name: /crear evento/i })).toBeInTheDocument();
    const user = await fillCreateForm();
    await user.click(screen.getByRole('button', { name: /guardar evento/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /detalle creado/i })).toBeInTheDocument());
  });

  it('redirige participante con mensaje de acceso restringido', async () => {
    seedAuthSession({ role: 'PARTICIPANTE' });
    renderWithProviders(<CreateRoutes />, { routerProps: { initialEntries: ['/eventos/nuevo'] } });

    expect(await screen.findByRole('heading', { name: /eventos disponibles/i })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Acceso restringido');
    expect(screen.queryByRole('heading', { name: /crear evento/i })).not.toBeInTheDocument();
  });
});
