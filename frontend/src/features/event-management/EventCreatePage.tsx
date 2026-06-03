import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ContextualError } from '../../components';
import { createEvent, type EventMutationInput } from '../../services/eventService';
import { type AppError, normalizeAppError } from '../../lib/errors';
import { Icon } from '../../shared/ui';
import { EventoForm } from './EventoForm';

export function EventCreatePage() {
  const navigate = useNavigate();
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<AppError | null>(null);

  const handleSubmit = async (values: EventMutationInput) => {
    setSubmitting(true);
    setError(null);
    try {
      const event = await createEvent(values);
      navigate(`/eventos/${event.id}`, { replace: true });
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="page-grid">
      <button className="button button--ghost" type="button" onClick={() => navigate('/catalogo')}>
        <Icon name="arrow-left" />
        Volver al catálogo
      </button>
      <div className="page-title">
        <div>
          <span className="eyebrow">Gestión de eventos</span>
          <h1>Crear evento</h1>
        </div>
      </div>
      {error && <ContextualError error={error} />}
      <EventoForm submitLabel="Guardar evento" isSubmitting={isSubmitting} onSubmit={handleSubmit} />
    </section>
  );
}
