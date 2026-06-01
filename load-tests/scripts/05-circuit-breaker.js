import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { authHeaders, authHeadersForIndex } from '../lib/auth.js';
import { INSCRIPTION_URL, bodyInscripcion, eventoId, tarifaId } from '../lib/helpers.js';
import { htmlReport, reportPath, textSummary } from '../lib/report.js';

const latenciaCircuitOpen = new Trend('latencia_con_circuit_open', true);
const retryAfterPresente = new Rate('retry_after_presente');
const startedAt = Date.now();
const WARMUP_MS = Number.parseInt(__ENV.CB_WARMUP_MS || '60000', 10);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    circuit_breaker_validation: {
      executor: 'constant-vus',
      vus: 100,
      duration: '2m',
    },
  },
  thresholds: {
    'http_req_duration{status:503,name:circuit_breaker_open}': ['p(95)<50'],
    retry_after_presente: ['rate>0.9'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  const warmupRequests = Number.parseInt(__ENV.CB_WARMUP_REQUESTS || '20', 10);
  for (let i = 0; i < warmupRequests; i += 1) {
    http.post(
      `${INSCRIPTION_URL}/api/v1/inscripciones`,
      bodyInscripcion(eventoId(1), tarifaId(1)),
      {
        headers: authHeadersForIndex(i),
        tags: { phase: 'warmup', name: 'circuit_breaker_warmup' },
        timeout: __ENV.CB_WARMUP_TIMEOUT || '5s',
        responseCallback: http.expectedStatuses(503),
      },
    );
    sleep(0.1);
  }
}

export default function () {
  if (__ITER === 0) {
    sleep(Math.random() * Number.parseFloat(__ENV.CB_INITIAL_JITTER_SECONDS || '60'));
  }

  const res = http.post(
    `${INSCRIPTION_URL}/api/v1/inscripciones`,
    bodyInscripcion(eventoId(1), tarifaId(1)),
    {
      headers: authHeaders(),
      tags: { expected_response: 'true', name: 'circuit_breaker_open' },
      timeout: __ENV.HTTP_TIMEOUT || '3s',
      responseCallback: http.expectedStatuses(503),
    },
  );

  const duration = res.timings.duration;
  const tieneRetryAfter = res.headers['Retry-After'] !== undefined || res.headers['retry-after'] !== undefined;
  const circuitShouldBeOpen = Date.now() - startedAt > WARMUP_MS;
  if (circuitShouldBeOpen) {
    latenciaCircuitOpen.add(duration);
    retryAfterPresente.add(tieneRetryAfter);
  }

  check(res, {
    'responde HTTP 503': (r) => r.status === 503,
    'incluye Retry-After': () => tieneRetryAfter,
    'degradacion controlada sin 5xx inesperado': (r) => r.status === 503,
  });

  const baseSleep = Number.parseFloat(__ENV.CB_THINK_TIME_SECONDS || '20');
  const jitterSleep = Number.parseFloat(__ENV.CB_JITTER_SECONDS || '10');
  sleep(baseSleep + (Math.random() * jitterSleep));
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('05-circuit-breaker.html')]: htmlReport(data, 'K6 RNF-14 Circuit Breaker'),
    [reportPath('05-circuit-breaker.json')]: JSON.stringify(data, null, 2),
  };
}
