import { useServiceHealth } from '../../hooks';
import { RetryAfterCountdown } from './RetryAfterCountdown';

export function DegradedServiceBanner() {
  const { services } = useServiceHealth();

  if (services.length === 0) return null;

  return (
    <div className="resilience-banner resilience-banner--degraded" role="status" aria-live="polite">
      <strong>Servicio en degradación controlada</strong>
      {services.map((service) => (
        <span key={service.serviceName}>
          {service.serviceName}: {service.message}{' '}
          <RetryAfterCountdown targetTimestamp={service.degradedUntil} />
        </span>
      ))}
    </div>
  );
}
