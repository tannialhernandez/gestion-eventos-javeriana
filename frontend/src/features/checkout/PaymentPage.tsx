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
            <dt>Inscripción</dt>
            <dd>{inscripcionId}</dd>
          </div>
          <div>
            <dt>Expira</dt>
            <dd>{formatDateTime(snapshot.expiresAt)}</dd>
          </div>
        </dl>
      </div>

      <section className="checkout-panel" aria-label="Resumen de pago">
        <h2>Resumen</h2>
        <div className="simulation-notice" role="note" aria-label="Modo simulación académica">
          <span className="simulation-badge">Modo simulación académica</span>
          <p>Pago procesado por simulador. Integración Mercado Pago disponible en Fase 2.</p>
          <div className="mercado-pago-mark" aria-label="Mercado Pago deshabilitado">
            <span aria-hidden="true">MP</span>
            <strong>Mercado Pago</strong>
          </div>
        </div>
        <div className="price-line">
          <span>Total</span>
          <strong>{formatMoney(snapshot.amount, snapshot.currency)}</strong>
        </div>
        {snapshot.checkoutUrl && (
          <a className="external-link" href={snapshot.checkoutUrl} target="_blank" rel="noreferrer">
            Abrir checkout simulado
          </a>
        )}
        {error && <ContextualError error={error} onRetry={handleApprove} />}
        <button className="button button--primary button--wide" type="button" onClick={handleApprove} disabled={isSubmitting}>
          <Icon name="check" />
          {isSubmitting ? 'Confirmando pago' : 'Confirmar pago aprobado'}
        </button>
      </section>
    </section>
  );
}
