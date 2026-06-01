import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ContextualError } from '../../components';
import type { AcademicEvent, Tariff } from '../../entities/event';
import { createInscription } from '../../services/inscriptionService';
import { getEvent, listTariffs } from '../../services/eventService';
import { BusinessRuleError, type AppError, normalizeAppError } from '../../lib/errors';
import { formatDate, formatDateTime, formatMoney, sanitizeText } from '../../shared/lib';
import { Icon, Skeleton, StatusBadge } from '../../shared/ui';
import { saveCheckoutSnapshot } from '../checkout/storage';

export function EventDetailPage() {
  const { eventoId } = useParams<{ eventoId: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<AcademicEvent | null>(null);
  const [tariffs, setTariffs] = useState<Tariff[]>([]);
  const [selectedTariffId, setSelectedTariffId] = useState<string>('');
  const [isLoading, setLoading] = useState(true);
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<AppError | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    if (!eventoId) return;
    let mounted = true;
    setLoading(true);
    setError(null);

    Promise.all([getEvent(eventoId), listTariffs(eventoId)])
      .then(([eventResponse, tariffResponse]) => {
        if (!mounted) return;
        setEvent(eventResponse);
        setTariffs(tariffResponse);
        setSelectedTariffId(tariffResponse[0]?.id ?? '');
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

  const selectedTariff = useMemo(() => tariffs.find((tariff) => tariff.id === selectedTariffId) ?? null, [tariffs, selectedTariffId]);
  const usedSeats = event ? event.cupoMaximo - event.cupoDisponible : 0;
  const seatsPercent = event ? Math.min(100, Math.round((usedSeats / event.cupoMaximo) * 100)) : 0;

  const handleCreateInscription = async () => {
    if (!event || !selectedTariff) return;
    setSubmitting(true);
    setError(null);
    try {
      const inscription = await createInscription(event.id, selectedTariff.id);
      saveCheckoutSnapshot(inscription.inscripcionId, {
        eventTitle: event.titulo,
        eventId: event.id,
        tariffId: selectedTariff.id,
        amount: selectedTariff.monto,
        currency: selectedTariff.moneda,
        checkoutUrl: inscription.checkoutUrl,
        expiresAt: inscription.fechaExpiracionPago,
      });
      navigate(`/inscripciones/${inscription.inscripcionId}/pago`);
    } catch (err) {
      const apiError = normalizeAppError(err);
      setError(
        apiError.status === 409
          ? new BusinessRuleError('El evento ya no tiene cupos disponibles.', { status: 409, code: 'sin_cupos_disponibles' })
          : apiError,
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading) return <Skeleton rows={2} />;

  if (error && !event) {
    return <ContextualError error={error} onRetry={() => setRetryVersion((current) => current + 1)} />;
  }

  if (!event) {
    return <div className="empty-state">El evento no está disponible.</div>;
  }

  return (
    <section className="detail-layout">
      <div className="detail-main">
        <button className="button button--ghost" type="button" onClick={() => navigate('/catalogo')}>
          <Icon name="arrow-left" />
          Volver
        </button>
        <div className="detail-heading">
          <div>
            <span className="eyebrow">{event.tipo}</span>
            <h1>{sanitizeText(event.titulo)}</h1>
          </div>
          <StatusBadge value={event.estado} />
        </div>
        <p className="detail-description">{sanitizeText(event.descripcion)}</p>

        <dl className="detail-facts">
          <div>
            <dt>Fecha de inicio</dt>
            <dd>{formatDate(event.fechaInicio)}</dd>
          </div>
          <div>
            <dt>Fecha de cierre</dt>
            <dd>{formatDate(event.fechaFin)}</dd>
          </div>
          <div>
            <dt>Límite de inscripción</dt>
            <dd>{formatDateTime(event.fechaLimiteInscripcion)}</dd>
          </div>
          <div>
            <dt>Modalidad</dt>
            <dd>{event.modalidad}</dd>
          </div>
        </dl>

        <div className="capacity-band">
          <div>
            <strong>{event.cupoDisponible} cupos disponibles</strong>
            <span>{usedSeats} de {event.cupoMaximo} reservados</span>
          </div>
          <div className="capacity-meter" aria-hidden="true">
            <span style={{ width: `${seatsPercent}%` }} />
          </div>
        </div>
      </div>

      <section className="checkout-panel" aria-label="Inscripción">
        <h2>Inscripción</h2>
        {tariffs.length > 0 ? (
          <>
            <label className="field">
              <span>Tarifa</span>
              <select className="select" value={selectedTariffId} onChange={(event) => setSelectedTariffId(event.target.value)}>
                {tariffs.map((tariff) => (
                  <option key={tariff.id} value={tariff.id}>
                    {tariff.descripcion} - {formatMoney(tariff.monto, tariff.moneda)}
                  </option>
                ))}
              </select>
            </label>
            <div className="price-line">
              <span>Total</span>
              <strong>{selectedTariff ? formatMoney(selectedTariff.monto, selectedTariff.moneda) : '-'}</strong>
            </div>
            {error && <ContextualError error={error} onRetry={handleCreateInscription} />}
            <button
              className="button button--primary button--wide"
              type="button"
              onClick={handleCreateInscription}
              disabled={!event.aceptaInscripciones || event.cupoDisponible <= 0 || isSubmitting}
            >
              <Icon name="credit-card" />
              {isSubmitting ? 'Reservando cupo' : 'Inscribirme y pagar'}
            </button>
          </>
        ) : (
          <div className="alert alert--warning">No hay tarifas activas para este evento.</div>
        )}
      </section>
    </section>
  );
}
