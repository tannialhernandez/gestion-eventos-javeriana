import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen } from '../../test/test-utils';
import { ServiceUnavailableError } from '../../lib/errors';
import { recordServiceDegraded, recordServiceRecovered } from '../../lib/serviceHealth';
import { DegradedServiceBanner } from './DegradedServiceBanner';

describe('DegradedServiceBanner', () => {
  afterEach(() => {
    recordServiceRecovered('event-service');
  });

  it('debe ocultarse cuando el estado es nominal', () => {
    renderWithProviders(<DegradedServiceBanner />);

    expect(screen.queryByText(/servicio en degradación controlada/i)).not.toBeInTheDocument();
  });

  it('debe renderizar cuando estado es degradado', () => {
    act(() => {
      recordServiceDegraded(new ServiceUnavailableError('Circuit Breaker abierto', {
        serviceName: 'event-service',
      }));
    });

    renderWithProviders(<DegradedServiceBanner />);

    expect(screen.getByRole('status')).toHaveTextContent(/servicio en degradación controlada/i);
    expect(screen.getByText(/event-service/i)).toBeInTheDocument();
  });

  it('debe mostrar cuenta regresiva del Retry-After', () => {
    vi.useFakeTimers();
    act(() => {
      recordServiceDegraded(new ServiceUnavailableError('Circuit Breaker abierto', {
        serviceName: 'event-service',
        retryAfterSeconds: 3,
      }));
    });

    renderWithProviders(<DegradedServiceBanner />);

    expect(screen.getByText(/reintento seguro en 3s/i)).toBeInTheDocument();
  });
});
