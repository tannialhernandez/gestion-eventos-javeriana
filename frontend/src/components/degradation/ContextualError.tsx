import { AuthError, BusinessRuleError, NetworkError, ServiceUnavailableError, normalizeAppError } from '../../lib/errors';
import { RetryAfterCountdown } from './RetryAfterCountdown';

type ContextualErrorProps = {
  error: unknown;
  onRetry?: () => void;
};

export function ContextualError({ error, onRetry }: ContextualErrorProps) {
  const appError = normalizeAppError(error);
  let title = 'No se pudo completar la operación';
  let description = appError.message;
  let variant = 'error';

  if (appError instanceof ServiceUnavailableError) {
    title = 'Servicio temporalmente no disponible';
    description = 'El backend abrió el Circuit Breaker para evitar una falla en cascada. La solicitud se reintentó respetando Retry-After.';
    variant = 'warning';
  } else if (appError instanceof NetworkError) {
    title = 'Problema de red';
    description = 'No pudimos contactar el servicio. Revisa tu conexión o intenta nuevamente.';
    variant = 'warning';
  } else if (appError instanceof AuthError) {
    title = 'Sesión expirada';
    description = 'Inicia sesión nuevamente para continuar con el flujo.';
  } else if (appError instanceof BusinessRuleError) {
    title = appError.status === 409 ? 'Cupo agotado' : 'Solicitud no disponible';
    description = appError.status === 409 ? 'El evento ya no tiene cupos disponibles. Puedes volver al catálogo y elegir otro evento.' : appError.message;
  }

  return (
    <div className={`contextual-error contextual-error--${variant}`} role="alert">
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
        {appError instanceof ServiceUnavailableError && (
          <RetryAfterCountdown retryAfterSeconds={appError.retryAfterSeconds} />
        )}
      </div>
      {onRetry && (
        <button className="button button--secondary" type="button" onClick={onRetry}>
          Reintentar
        </button>
      )}
    </div>
  );
}
