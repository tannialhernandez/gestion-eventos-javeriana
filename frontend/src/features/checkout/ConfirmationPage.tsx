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
      <h1>Inscripción confirmada</h1>
      <p>
        {snapshot
          ? `Tu cupo para ${snapshot.eventTitle} quedó reservado y el pago fue confirmado.`
          : 'Tu cupo quedó reservado y el pago fue confirmado.'}
      </p>
      <code>{inscripcionId}</code>
      <Link className="button button--secondary" to="/catalogo">
        Volver al catálogo
      </Link>
    </section>
  );
}
