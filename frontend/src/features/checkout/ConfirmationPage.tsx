import { Link, useParams } from 'react-router-dom';
import { Icon } from '../../shared/ui';
import { readCheckoutSnapshot } from './storage';

export function ConfirmationPage() {
  const { inscripcionId } = useParams<{ inscripcionId: string }>();
  const snapshot = readCheckoutSnapshot(inscripcionId);

  return (
    <section className="confirmation">
      <div className="confirmation__mark">
        <Icon name="check" size={36} />
      </div>
      <span className="eyebrow">Confirmación recibida</span>
      <h1>Inscripción en proceso de confirmación</h1>
      <p>
        {snapshot
          ? `El pago para ${snapshot.eventTitle} fue notificado al payment-service.`
          : 'El pago fue notificado al payment-service.'}
      </p>
      <code>{inscripcionId}</code>
      <Link className="button button--secondary" to="/catalogo">
        Volver al catálogo
      </Link>
    </section>
  );
}
