import type { AxiosError } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { AuthError, BusinessRuleError, NetworkError, ServiceUnavailableError } from './AppError';
import { parseHttpError, parseRetryAfter } from './parseHttpError';

function axiosError(status?: number, data: Record<string, unknown> = {}, headers: Record<string, string> = {}): AxiosError<Record<string, unknown>> {
  return {
    name: 'AxiosError',
    message: 'Request failed',
    isAxiosError: true,
    toJSON: () => ({}),
    response: status
      ? {
        status,
        statusText: String(status),
        headers,
        data,
        config: {},
      }
      : undefined,
  } as AxiosError<Record<string, unknown>>;
}

describe('parseHttpError', () => {
  it('debe clasificar 503 como ServiceUnavailableError', () => {
    const error = parseHttpError(axiosError(503, { message: 'Downstream caido' }, { 'retry-after': '10' }), 'event-service');

    expect(error).toBeInstanceOf(ServiceUnavailableError);
    expect(error.retryAfterSeconds).toBe(10);
    expect(error.serviceName).toBe('event-service');
  });

  it('debe clasificar 401 como AuthError', () => {
    expect(parseHttpError(axiosError(401), 'auth-service-stub')).toBeInstanceOf(AuthError);
  });

  it('debe clasificar 409 como BusinessRuleError', () => {
    expect(parseHttpError(axiosError(409, { message: 'Sin cupos' }), 'inscription-service')).toBeInstanceOf(BusinessRuleError);
  });

  it('debe clasificar ausencia de response como NetworkError', () => {
    expect(parseHttpError(axiosError(), 'event-service')).toBeInstanceOf(NetworkError);
  });
});

describe('parseRetryAfter', () => {
  it('debe leer segundos numericos', () => {
    expect(parseRetryAfter('30')).toBe(30);
  });

  it('debe leer fechas HTTP futuras', () => {
    vi.setSystemTime(new Date('2026-05-31T12:00:00Z'));

    expect(parseRetryAfter('Sun, 31 May 2026 12:00:05 GMT')).toBe(5);
  });
});
