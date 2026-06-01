import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders } from '../lib/auth.js';
import { eventoId, tarifaId, postInscripcion } from '../lib/helpers.js';
import { htmlReport, reportPath, textSummary } from '../lib/report.js';

const inscripcionesConfirmadas = new Counter('inscripciones_confirmadas');
const inscripcionesRechazadasPorCupo = new Counter('inscripciones_rechazadas_por_cupo');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    rnf_16_concurrencia_cupos: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 50,
      maxDuration: '2m',
    },
  },
  thresholds: {
    inscripciones_confirmadas: ['count==10'],
    inscripciones_rechazadas_por_cupo: ['count==40'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate==1'],
  },
};

const EVENTO_LIMITADO_ID = eventoId(10);
const TARIFA_LIMITADA_ID = tarifaId(10);

export default function () {
  const res = postInscripcion(EVENTO_LIMITADO_ID, TARIFA_LIMITADA_ID, authHeaders(), {
    name: 'cupos_concurrencia',
  });

  if (res.status === 201) inscripcionesConfirmadas.add(1);
  else if (res.status === 409) inscripcionesRechazadasPorCupo.add(1);

  check(res, {
    'no genera 5xx': (r) => r.status < 500,
    'estado esperado 201 o 409': (r) => r.status === 201 || r.status === 409,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [reportPath('04-cupos-concurrencia.html')]: htmlReport(data, 'K6 RNF-16 Cupos Concurrencia'),
    [reportPath('04-cupos-concurrencia.json')]: JSON.stringify(data, null, 2),
  };
}
