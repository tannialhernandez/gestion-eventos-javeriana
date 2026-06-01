import { useSyncExternalStore } from 'react';
import {
  getServiceHealthSnapshot,
  subscribeServiceHealth,
  type ServiceHealthState,
} from '../lib/serviceHealth';

const emptySnapshot: ServiceHealthState[] = [];

export function useServiceHealth(serviceName?: string) {
  const services = useSyncExternalStore(subscribeServiceHealth, getServiceHealthSnapshot, () => emptySnapshot);
  const filtered = serviceName ? services.filter((service) => service.serviceName === serviceName) : services;

  return {
    degraded: filtered.some((service) => service.degraded),
    services: filtered,
  };
}
