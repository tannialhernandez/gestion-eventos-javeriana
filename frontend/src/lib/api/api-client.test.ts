import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createHttpClient, writeStoredSession } from '../../shared/api/http';
import { server } from '../../test/mocks/server';
import { AuthError, BusinessRuleError, ServiceUnavailableError } from '../errors';

describe('createHttpClient', () => {
  it('debe inyectar Authorization header', async () => {
    let authorization: string | null = null;
    server.use(http.get('http://localhost/api-test/probe', ({ request }) => {
      authorization = request.headers.get('authorization');
      return HttpResponse.json({ ok: true });
    }));
    writeStoredSession({ token: 'token-test', expiresAt: '2033-05-31T12:00:00Z' });

    await createHttpClient('http://localhost/api-test', 'test-service').get('/probe');

    expect(authorization).toBe('Bearer token-test');
  });

  it('debe inyectar X-Correlation-Id', async () => {
    let correlationId: string | null = null;
    server.use(http.get('http://localhost/api-test/probe', ({ request }) => {
      correlationId = request.headers.get('x-correlation-id');
      return HttpResponse.json({ ok: true });
    }));

    await createHttpClient('http://localhost/api-test', 'test-service').get('/probe');

    expect(correlationId).toMatch(/[0-9a-f-]{36}/i);
  });

  it('debe transformar 503 con Retry-After en ServiceUnavailableError', async () => {
    server.use(http.get('http://localhost/api-test/probe', () => (
      HttpResponse.json({ message: 'Circuit Breaker abierto' }, {
        status: 503,
        headers: { 'retry-after': '30' },
      })
    )));

    await expect(createHttpClient('http://localhost/api-test', 'event-service').get('/probe'))
      .rejects.toMatchObject({
        name: 'ServiceUnavailableError',
        serviceName: 'event-service',
        retryAfterSeconds: 30,
      });
  });

  it('debe transformar 401 en AuthError', async () => {
    server.use(http.get('http://localhost/api-test/probe', () => HttpResponse.json({ message: 'Sesion expirada' }, { status: 401 })));

    await expect(createHttpClient('http://localhost/api-test', 'auth-service-stub').get('/probe'))
      .rejects.toBeInstanceOf(AuthError);
  });

  it('debe transformar 409 en BusinessRuleError', async () => {
    server.use(http.get('http://localhost/api-test/probe', () => HttpResponse.json({ message: 'Sin cupos' }, { status: 409 })));

    await expect(createHttpClient('http://localhost/api-test', 'inscription-service').get('/probe'))
      .rejects.toBeInstanceOf(BusinessRuleError);
  });

  it('debe conservar la clase concreta para degradacion controlada', async () => {
    server.use(http.get('http://localhost/api-test/probe', () => HttpResponse.json({}, { status: 503 })));

    await expect(createHttpClient('http://localhost/api-test', 'event-service').get('/probe'))
      .rejects.toBeInstanceOf(ServiceUnavailableError);
  });
});
