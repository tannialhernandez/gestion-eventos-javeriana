import type { ServiceUnavailableError } from '../errors';

export type ServiceHealthState = {
  serviceName: string;
  degraded: boolean;
  message: string;
  degradedUntil?: number;
  lastChangedAt: number;
};

type Listener = () => void;

const states = new Map<string, ServiceHealthState>();
const listeners = new Set<Listener>();
let snapshot: ServiceHealthState[] = [];

function refreshSnapshot() {
  snapshot = Array.from(states.values()).sort((left, right) => left.serviceName.localeCompare(right.serviceName));
}

function notify() {
  refreshSnapshot();
  listeners.forEach((listener) => listener());
}

export function subscribeServiceHealth(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getServiceHealthSnapshot(): ServiceHealthState[] {
  return snapshot;
}

export function recordServiceDegraded(error: ServiceUnavailableError): void {
  const serviceName = error.serviceName ?? 'servicio';
  states.set(serviceName, {
    serviceName,
    degraded: true,
    message: error.message,
    degradedUntil: error.retryAfterSeconds ? Date.now() + error.retryAfterSeconds * 1000 : undefined,
    lastChangedAt: Date.now(),
  });
  notify();

  if (error.retryAfterSeconds) {
    window.setTimeout(() => {
      recordServiceRecovered(serviceName);
    }, error.retryAfterSeconds * 1000);
  }
}

export function recordServiceRecovered(serviceName: string): void {
  if (!states.has(serviceName)) return;
  states.delete(serviceName);
  notify();
}
