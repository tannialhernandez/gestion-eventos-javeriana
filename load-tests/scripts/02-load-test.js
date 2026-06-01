import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
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
const latenciaCrearInscripcion = new Trend('latencia_crear_inscripcion', true);
const INSCRIPTION_INTERVAL = Number.parseInt(__ENV.INSCRIPTION_INTERVAL || '0', 10);

function debeCrearInscripcion() {
  if (INSCRIPTION_INTERVAL <= 0) return false;
  return ((__VU * 31 + __ITER) % INSCRIPTION_INTERVAL) === 0;
}

export const options = {
  discardResponseBodies: true,
  scenarios: {
    load_sostenido_150_vus: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 150 },
        { duration: '3m', target: 150 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    http_req_failed: ['rate<0.02'],
  },
};

export function setup() {
  for (let intento = 0; intento < 5; intento += 1) {
    const res = http.get(`${EVENT_URL}/api/v1/eventos?pagina=0&tamano=20`, {
      tags: { name: 'catalogo_warmup' },
      timeout: __ENV.HTTP_TIMEOUT || '5s',
      responseCallback: http.expectedStatuses(200),
    });

    if (res.status === 200) {
      return;
    }

    sleep(1);
  }
}

export default function () {
  if (__ITER === 0) {
    sleep(randomBetween(0, 15));
  }

  const headers = authHeaders();

  group('1. Consultar catalogo', () => {
    const res = http.get(`${EVENT_URL}/api/v1/eventos?pagina=0&tamano=20`, {
      tags: { name: 'catalogo' },
      timeout: __ENV.HTTP_TIMEOUT || '5s',
      responseCallback: http.expectedStatuses(200),
    });
    check(res, {
      'catalogo status 200': (r) => r.status === 200,
    });
  });

  sleep(randomBetween(8, 15));

  if (debeCrearInscripcion()) {
    group('2. Crear inscripcion', () => {
      const numeroEvento = randomEventoNumber();
      const res = postInscripcion(eventoId(numeroEvento), tarifaId(numeroEvento), headers, {
        name: 'crear_inscripcion',
      });

      latenciaCrearInscripcion.add(res.timings.duration);

      check(res, {
        'inscripcion status 201 o 409 esperados': (r) => r.status === 201 || r.status === 409,
        'inscripcion no es 5xx': (r) => r.status < 500,
      });

      if (res.status === 201) inscripcionesCreadas.add(1);
      else if (res.status === 409) inscripcionesRechazadas.add(1);
    });
  }

  sleep(randomBetween(25, 45));
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('02-load-test.html')]: htmlReport(data, 'K6 Load Sostenido 150 VUs'),
    [reportPath('02-load-test.json')]: JSON.stringify(data, null, 2),
  };
}
