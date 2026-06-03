import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent, waitFor } from '../../test/test-utils';
import { EventoForm } from './EventoForm';

async function fillRequiredFields() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/título/i), 'Seminario de Prueba');
  await user.type(screen.getByLabelText(/descripción/i), 'Evento académico creado desde pruebas.');
  await user.clear(screen.getByLabelText(/fecha inicio/i));
  await user.type(screen.getByLabelText(/fecha inicio/i), '2033-06-10');
  await user.clear(screen.getByLabelText(/fecha fin/i));
  await user.type(screen.getByLabelText(/fecha fin/i), '2033-06-11');
  await user.clear(screen.getByLabelText(/fecha límite de inscripción/i));
  await user.type(screen.getByLabelText(/fecha límite de inscripción/i), '2033-06-01T23:59');
  await user.clear(screen.getByLabelText(/lugar/i));
  await user.type(screen.getByLabelText(/lugar/i), 'Auditorio Javeriana');
  await user.clear(screen.getByLabelText(/capacidad/i));
  await user.type(screen.getByLabelText(/capacidad/i), '80');
  await user.clear(screen.getByLabelText(/tarifa monto/i));
  await user.type(screen.getByLabelText(/tarifa monto/i), '120000');
  await user.selectOptions(screen.getByLabelText(/estado/i), 'PENDIENTE_PUBLICACION');
  return user;
}

describe('EventoForm', () => {
  it('valida campos requeridos antes de enviar', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    renderWithProviders(<EventoForm submitLabel="Guardar evento" onSubmit={onSubmit} />);

    await user.click(screen.getByRole('button', { name: /guardar evento/i }));

    expect(screen.getByText(/el título es obligatorio/i)).toBeInTheDocument();
    expect(screen.getByText(/la descripción es obligatoria/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('envia valores validos del formulario', async () => {
    const onSubmit = vi.fn();
    renderWithProviders(<EventoForm submitLabel="Guardar evento" onSubmit={onSubmit} />);

    const user = await fillRequiredFields();
    await user.click(screen.getByRole('button', { name: /guardar evento/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      titulo: 'Seminario de Prueba',
      descripcion: 'Evento académico creado desde pruebas.',
      fechaInicio: '2033-06-10',
      fechaFin: '2033-06-11',
      fechaLimiteInscripcion: '2033-06-01T23:59',
      cupoMaximo: 80,
      tarifaMonto: 120000,
      estado: 'PENDIENTE_PUBLICACION',
    }));
  });
});
