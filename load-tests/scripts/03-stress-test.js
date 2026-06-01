import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders } from '../lib/auth.js';
import {
  EVENT_URL,
  eventoId,
  postInscripcion,
  randomBetween,
  randomEventoNumber,
  tarifaId,
} from '../lib/helpers.js';
import { htmlReport, reportPath, textSummary } from '../lib/report.js';

const inscripcionesCreadas = new Counter('inscripciones_creadas_total');
const inscripcionesRechazadas = new Counter('inscripciones_rechazadas_total');
const INSCRIPTION_PROBABILITY = Number.parseFloat(__ENV.STRESS_INSCRIPTION_PROBABILITY || '0.10');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    stress_ramp_1000: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 250 },
        { duration: '1m', target: 500 },
        { duration: '1m', target: 750 },
        { duration: '1m', target: 1000 },
        { duration: '1m', target: 1000 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<1500'],
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:catalogo}': ['p(95)<500'],
  },
};

export default function () {
  const headers = authHeaders();

  group('catalogo bajo stress', () => {
    const res = http.get(`${EVENT_URL}/api/v1/eventos?page=0&size=20`, {
      tags: { name: 'catalogo' },
      timeout: __ENV.HTTP_TIMEOUT || '5s',
      responseCallback: http.expectedStatuses(200),
    });
    check(res, {
      'catalogo responde': (r) => r.status === 200,
      'catalogo sin timeout': (r) => r.timings.duration < 1500,
    });
  });

  if (Math.random() < INSCRIPTION_PROBABILITY) {
    group('inscripcion bajo stress', () => {
      const numeroEvento = randomEventoNumber();
      const res = postInscripcion(eventoId(numeroEvento), tarifaId(numeroEvento), headers, {
        name: 'crear_inscripcion',
      });
      check(res, {
        'estado esperado': (r) => r.status === 201 || r.status === 409 || r.status === 422,
        'sin 5xx': (r) => r.status < 500,
      });

      if (res.status === 201) inscripcionesCreadas.add(1);
      else if (res.status === 409 || res.status === 422) inscripcionesRechazadas.add(1);
    });
  }

  sleep(randomBetween(5, 15));
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('03-stress-test.html')]: htmlReport(data, 'K6 Stress Test 0 a 1000 VUs'),
    [reportPath('03-stress-test.json')]: JSON.stringify(data, null, 2),
  };
}
