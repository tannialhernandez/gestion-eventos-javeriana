import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { EVENT_URL } from '../lib/helpers.js';
import { htmlReport, reportPath, textSummary } from '../lib/report.js';

const cacheHitRateEstimado = new Rate('cache_hit_rate_estimado');
const redisGets = new Counter('redis_get_total');
const redisSetex = new Counter('redis_setex_total');
const cacheHitsEstimados = new Counter('cache_hits_estimados_total');
const catalogoWarmDuration = new Trend('catalogo_warm_duration', true);

const ITERACIONES_WARM = Number.parseInt(__ENV.CACHE_WARM_ITERATIONS || '200', 10);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    cache_effectiveness: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    cache_hit_rate_estimado: ['rate>0.95'],
    catalogo_warm_duration: ['p(95)<300'],
    'http_req_duration{name:catalogo_warm}': ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate==1'],
  },
};

function prometheus() {
  const res = http.get(`${EVENT_URL}/actuator/prometheus`, {
    tags: { name: 'prometheus_event_service' },
    timeout: __ENV.HTTP_TIMEOUT || '5s',
    responseCallback: http.expectedStatuses(200),
  });
  check(res, {
    'prometheus event-service responde': (r) => r.status === 200,
  });
  return res.body || '';
}

function lettuceCount(body, command) {
  const escaped = command.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const regex = new RegExp(
    `lettuce_command_completion_seconds_count\\{[^}]*command="${escaped}"[^}]*\\}\\s+([0-9.]+)`,
    'g',
  );
  let total = 0;
  let match;
  while ((match = regex.exec(body)) !== null) {
    total += Number.parseFloat(match[1]);
  }
  return total;
}

function getCatalogo(name) {
  return http.get(`${EVENT_URL}/api/v1/eventos?pagina=0&tamano=20`, {
    tags: { name },
    timeout: __ENV.HTTP_TIMEOUT || '5s',
    responseCallback: http.expectedStatuses(200),
  });
}

export default function () {
  const antes = prometheus();
  const getsAntes = lettuceCount(antes, 'GET');
  const setexAntes = lettuceCount(antes, 'SETEX');

  const cold = getCatalogo('catalogo_cold');
  check(cold, {
    'catalogo cold responde': (r) => r.status === 200,
  });

  sleep(0.2);

  for (let i = 0; i < ITERACIONES_WARM; i += 1) {
    const warm = getCatalogo('catalogo_warm');
    catalogoWarmDuration.add(warm.timings.duration);
    check(warm, {
      'catalogo warm responde': (r) => r.status === 200,
    });
    sleep(0.02);
  }

  const despues = prometheus();
  const getDelta = Math.max(0, lettuceCount(despues, 'GET') - getsAntes);
  const setexDelta = Math.max(0, lettuceCount(despues, 'SETEX') - setexAntes);
  const hitsEstimados = Math.max(0, getDelta - setexDelta);
  const missesEstimados = Math.max(0, Math.min(setexDelta, getDelta));

  redisGets.add(getDelta);
  redisSetex.add(setexDelta);
  cacheHitsEstimados.add(hitsEstimados);

  for (let i = 0; i < hitsEstimados; i += 1) {
    cacheHitRateEstimado.add(true);
  }
  for (let i = 0; i < missesEstimados; i += 1) {
    cacheHitRateEstimado.add(false);
  }

  check(null, {
    'redis GET observado': () => getDelta > 0,
    'cache hit estimado >= 95%': () => getDelta > 0 && (hitsEstimados / getDelta) >= 0.95,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('06-cache-effectiveness.html')]: htmlReport(data, 'K6 Cache Effectiveness Redis'),
    [reportPath('06-cache-effectiveness.json')]: JSON.stringify(data, null, 2),
  };
}
