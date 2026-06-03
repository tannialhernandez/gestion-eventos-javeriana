import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ContextualError } from '../../components';
import type { AcademicEvent, Tariff } from '../../entities/event';
import { cancelEvent, getEvent, listTariffs, updateEvent, type EventMutationInput } from '../../services/eventService';
import { type AppError, normalizeAppError } from '../../lib/errors';
import { Icon, Skeleton } from '../../shared/ui';
import { EventoForm } from './EventoForm';

export function EventEditPage() {
  const { eventoId } = useParams<{ eventoId: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<AcademicEvent | null>(null);
  const [tariff, setTariff] = useState<Tariff | null>(null);
  const [isLoading, setLoading] = useState(true);
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<AppError | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    if (!eventoId) return;
    let mounted = true;
    setLoading(true);
    setError(null);

    Promise.all([getEvent(eventoId), listTariffs(eventoId).catch(() => [])])
      .then(([eventResponse, tariffs]) => {
        if (!mounted) return;
        setEvent(eventResponse);
        setTariff(tariffs[0] ?? null);
      })
      .catch((err) => {
        if (mounted) setError(normalizeAppError(err));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [eventoId, retryVersion]);

  const handleSubmit = async (values: EventMutationInput) => {
    if (!eventoId) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await updateEvent(eventoId, values);
      navigate(`/eventos/${updated.id}`, { replace: true });
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!eventoId || !window.confirm('¿Cancelar este evento?')) return;
    setSubmitting(true);
    setError(null);
    try {
      await cancelEvent(eventoId, 'Cancelado desde edición de evento');
      navigate(`/eventos/${eventoId}`, { replace: true });
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading) return <Skeleton rows={2} />;

  if (error && !event) {
    return <ContextualError error={error} onRetry={() => setRetryVersion((current) => current + 1)} />;
  }

  if (!event) return <div className="empty-state">El evento no está disponible.</div>;

  return (
    <section className="page-grid">
      <button className="button button--ghost" type="button" onClick={() => navigate(`/eventos/${event.id}`)}>
        <Icon name="arrow-left" />
        Volver al detalle
      </button>
      <div className="page-title">
        <div>
          <span className="eyebrow">Gestión de eventos</span>
          <h1>Editar evento</h1>
        </div>
        <button className="button button--danger" type="button" onClick={handleCancel} disabled={isSubmitting || ['CANCELADO', 'FINALIZADO'].includes(event.estado)}>
          <Icon name="trash" />
          Cancelar evento
        </button>
      </div>
      {error && <ContextualError error={error} />}
      <EventoForm
        initialEvent={event}
        initialTariff={tariff}
        submitLabel="Guardar cambios"
        isSubmitting={isSubmitting}
        onSubmit={handleSubmit}
      />
    </section>
  );
}
