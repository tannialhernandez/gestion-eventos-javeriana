import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ContextualError } from '../../components';
import { approvePayment } from '../../services/paymentService';
import { type AppError, normalizeAppError } from '../../lib/errors';
import { formatDateTime, formatMoney, sanitizeText } from '../../shared/lib';
import { Icon } from '../../shared/ui';
import { readCheckoutSnapshot } from './storage';

export function PaymentPage() {
  const { inscripcionId } = useParams<{ inscripcionId: string }>();
  const navigate = useNavigate();
  const snapshot = readCheckoutSnapshot(inscripcionId);
  const [isSubmitting, setSubmitting] = useState(false);
  const [error, setError] = useState<AppError | null>(null);

  const handleApprove = async () => {
    if (!inscripcionId) return;
    setSubmitting(true);
    setError(null);
    try {
      await approvePayment(inscripcionId);
      navigate(`/confirmacion/${inscripcionId}`, { replace: true });
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (!snapshot) {
    return (
      <div className="empty-state">
        No hay una inscripción activa en esta sesión.
        <button className="button button--secondary" type="button" onClick={() => navigate('/catalogo')}>
          Volver al catálogo
        </button>
      </div>
    );
  }

  return (
    <section className="payment-layout">
      <div className="payment-summary">
        <span className="eyebrow">Pago de inscripción</span>
        <h1>{sanitizeText(snapshot.eventTitle)}</h1>
        <dl className="detail-facts">
          <div>
            <dt>Referencia</dt>
            <dd>{inscripcionId}</dd>
          </div>
          <div>
            <dt>Expira</dt>
            <dd>{formatDateTime(snapshot.expiresAt)}</dd>
          </div>
        </dl>
      </div>

      <section className="checkout-panel" aria-label="Resumen de pago">
        <h2>Resumen del pago</h2>
        <div className="price-line">
          <span>Total a pagar</span>
          <strong>{formatMoney(snapshot.amount, snapshot.currency)}</strong>
        </div>
        {error && <ContextualError error={error} onRetry={handleApprove} />}
        <button
          className="button button--primary button--wide"
          type="button"
          onClick={handleApprove}
          disabled={isSubmitting}
        >
          <Icon name="check" />
          {isSubmitting ? 'Procesando pago…' : 'Confirmar y pagar'}
        </button>
      </section>
    </section>
  );
}
