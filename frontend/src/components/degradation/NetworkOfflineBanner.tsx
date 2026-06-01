import { useNetworkStatus } from '../../hooks';

export function NetworkOfflineBanner() {
  const { online } = useNetworkStatus();

  if (online) return null;

  return (
    <div className="resilience-banner resilience-banner--offline" role="status" aria-live="assertive">
      <strong>Sin conexión</strong>
      <span>Tu navegador está sin red. Conserva esta pantalla abierta y vuelve a intentar cuando recuperes conexión.</span>
    </div>
  );
}
