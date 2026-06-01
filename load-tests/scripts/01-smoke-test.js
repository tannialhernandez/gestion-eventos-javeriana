import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../lib/auth.js';
import { EVENT_URL, eventoId, tarifaId, postInscripcion } from '../lib/helpers.js';
import { htmlReport, reportPath, textSummary } from '../lib/report.js';

const inscripcionesCreadas = new Counter('inscripciones_creadas_total');
const latenciaCrearInscripcion = new Trend('latencia_crear_inscripcion', true);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 1,
      duration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{name:catalogo}': ['p(95)<1000'],
    latencia_crear_inscripcion: ['p(95)<1000'],
  },
};

export default function () {
  const headers = authHeaders();
  const numeroEvento = 20 + (__ITER % 40);

  group('catalogo eventos', () => {
    const res = http.get(`${EVENT_URL}/api/v1/eventos?page=0&size=20`, {
      tags: { name: 'catalogo' },
      timeout: __ENV.HTTP_TIMEOUT || '5s',
      responseCallback: http.expectedStatuses(200),
    });
    check(res, {
      'catalogo status 200': (r) => r.status === 200,
      'catalogo responde en menos de 1000ms': (r) => r.timings.duration < 1000,
    });
  });

  sleep(1);

  group('crear inscripcion', () => {
    const res = postInscripcion(eventoId(numeroEvento), tarifaId(numeroEvento), headers, {
      name: 'crear_inscripcion',
    });
    latenciaCrearInscripcion.add(res.timings.duration);

    check(res, {
      'inscripcion status esperado': (r) => r.status === 201 || r.status === 409,
      'inscripcion sin 5xx': (r) => r.status < 500,
    });

    if (res.status === 201) inscripcionesCreadas.add(1);
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('01-smoke-test.html')]: htmlReport(data, 'K6 Smoke Test'),
    [reportPath('01-smoke-test.json')]: JSON.stringify(data, null, 2),
  };
}
