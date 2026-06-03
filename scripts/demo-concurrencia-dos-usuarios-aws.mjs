import crypto from 'node:crypto';

const baseUrl = process.env.BASE_URL ?? 'https://d1xvny1kolb55e.cloudfront.net';
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);

const users = {
  organizador: {
    label: 'ORGANIZADOR',
    email: process.env.AWS_ORGANIZADOR_EMAIL ?? 'carlos.organizador@javeriana.edu.co',
    password: process.env.AWS_ORGANIZADOR_PASSWORD ?? 'demo123',
  },
  admin: {
    label: 'ADMIN',
    email: process.env.AWS_ADMIN_EMAIL ?? 'ana.admin@javeriana.edu.co',
    password: process.env.AWS_ADMIN_PASSWORD ?? 'demo123',
  },
  primero: {
    label: process.env.AWS_PRIMER_LABEL ?? 'Laura',
    email: process.env.AWS_PRIMER_EMAIL ?? 'laura.participante@javeriana.edu.co',
    password: process.env.AWS_PRIMER_PASSWORD ?? 'demo123',
  },
  segundo: {
    label: process.env.AWS_SEGUNDO_LABEL ?? 'Sofia',
    email: process.env.AWS_SEGUNDO_EMAIL ?? 'sofia.soporte@javeriana.edu.co',
    password: process.env.AWS_SEGUNDO_PASSWORD ?? 'demo123',
  },
};

function fail(message) {
  throw new Error(`[demo-concurrencia-aws] ${message}`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function requestJson(method, path, { body, token } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      Connection: 'close',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  return { status: response.status, data };
}

function assertStatus(label, result, expected) {
  if (!expected.includes(result.status)) {
    fail(`${label} retorno HTTP ${result.status}: ${JSON.stringify(result.data)}`);
  }
}

async function login(user) {
  const result = await requestJson('POST', '/api/v1/auth/login', {
    body: { email: user.email, password: user.password },
  });
  assertStatus(`login ${user.label}`, result, [200]);
  const token = result.data.accessToken ?? result.data.access_token;
  if (!token) fail(`login ${user.label} no retorno accessToken`);
  return token;
}

async function crearEvento(token) {
  const payload = {
    titulo: `Demo concurrencia 2 usuarios ${stamp}`,
    descripcion: 'Evento temporal AWS: cupo unico para probar bloqueo pesimista y no sobrecupo.',
    tipo: 'SEMINARIO',
    modalidad: 'VIRTUAL',
    fechaInicio: '2033-11-10',
    fechaFin: '2033-11-10',
    fechaLimiteInscripcion: '2033-11-01T23:59:00',
    cupoMaximo: 1,
  };
  const result = await requestJson('POST', '/api/v1/eventos', { token, body: payload });
  assertStatus('crear evento', result, [201]);
  return result.data;
}

async function crearTarifa(token, eventoId) {
  const result = await requestJson('POST', '/api/v1/tarifas', {
    token,
    body: {
      eventoId,
      descripcion: 'Tarifa demo concurrencia AWS',
      monto: 150000,
      moneda: 'COP',
    },
  });
  assertStatus('crear tarifa', result, [201]);
  return result.data;
}

async function inscribirParticipante(participante, token, eventoId, tarifaId) {
  const inicio = performance.now();
  const result = await requestJson('POST', '/api/v1/inscripciones', {
    token,
    body: {
      eventoId,
      tarifaId,
      idempotencyKey: crypto.randomUUID(),
    },
  });
  return {
    participante,
    status: result.status,
    ms: Math.round(performance.now() - inicio),
    data: result.data,
  };
}

async function esperarEstadoInscripcion(token, eventoId, estadoEsperado) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const result = await requestJson('GET', `/api/v1/inscripciones/mia?eventoId=${eventoId}`, { token });
    if (result.status === 200 && result.data.estado === estadoEsperado) return result.data;
    await sleep(1000);
  }
  fail(`la inscripcion no llego a estado ${estadoEsperado}`);
}

async function obtenerEvento(eventoId) {
  const result = await requestJson('GET', `/api/v1/eventos/${eventoId}?ts=${Date.now()}`);
  assertStatus('consultar evento', result, [200]);
  return result.data;
}

console.log(`[demo-concurrencia-aws] Base URL: ${baseUrl}`);

const organizadorToken = await login(users.organizador);
const adminToken = await login(users.admin);
const primerToken = await login(users.primero);
const segundoToken = await login(users.segundo);

let eventoId = null;
let inscripcionExitosa = null;
let tokenExitoso = null;

try {
  const evento = await crearEvento(organizadorToken);
  eventoId = evento.id;
  const tarifa = await crearTarifa(organizadorToken, eventoId);

  assertStatus('enviar a revision', await requestJson('POST', `/api/v1/eventos/${eventoId}/enviar-revision`, {
    token: organizadorToken,
  }), [200]);
  assertStatus('aprobar evento', await requestJson('POST', `/api/v1/eventos/${eventoId}/aprobar`, {
    token: adminToken,
  }), [200]);

  const antes = await obtenerEvento(eventoId);
  console.log(`[demo-concurrencia-aws] Evento publicado=${eventoId} cupoAntes=${antes.cupoDisponible}/${antes.cupoMaximo}`);

  const [laura, diego] = await Promise.all([
    inscribirParticipante(users.primero.label, primerToken, eventoId, tarifa.id),
    inscribirParticipante(users.segundo.label, segundoToken, eventoId, tarifa.id),
  ]);

  const resultados = [laura, diego];
  const exitosas = resultados.filter((r) => [200, 201].includes(r.status));
  const rechazadas = resultados.filter((r) => r.status === 409);

  for (const r of resultados) {
    console.log(`[demo-concurrencia-aws] ${r.participante}: HTTP ${r.status} en ${r.ms}ms estado=${r.data?.estado ?? r.data?.error ?? 'n/a'}`);
  }

  if (exitosas.length !== 1 || rechazadas.length !== 1) {
    fail(`se esperaba 1 inscripcion exitosa y 1 rechazo por cupo. Resultado=${JSON.stringify(resultados)}`);
  }

  inscripcionExitosa = exitosas[0].data;
  tokenExitoso = exitosas[0].participante === users.primero.label ? primerToken : segundoToken;

  const despuesReserva = await obtenerEvento(eventoId);
  if (despuesReserva.cupoDisponible !== 0) {
    fail(`despues de la concurrencia el cupo deberia ser 0, fue ${despuesReserva.cupoDisponible}`);
  }

  const pago = await requestJson('POST', `/api/v1/pagos/simulador/${inscripcionExitosa.inscripcionId}/aprobar`);
  assertStatus('aprobar pago simulado', pago, [200]);
  await esperarEstadoInscripcion(tokenExitoso, eventoId, 'CONFIRMADA');

  const cancelada = await requestJson('POST', `/api/v1/inscripciones/${inscripcionExitosa.inscripcionId}/cancelar`, {
    token: tokenExitoso,
  });
  assertStatus('cancelar inscripcion y reembolsar', cancelada, [200]);

  const despuesCancelacion = await obtenerEvento(eventoId);
  if (despuesCancelacion.cupoDisponible !== 1) {
    fail(`despues de cancelar el cupo deberia volver a 1, fue ${despuesCancelacion.cupoDisponible}`);
  }

  console.log('[demo-concurrencia-aws] Evidencia OK');
  console.log(JSON.stringify({
    eventoId,
    tarifaId: tarifa.id,
    cupoInicial: antes.cupoDisponible,
    concurrencia: resultados.map((r) => ({
      participante: r.participante,
      http: r.status,
      ms: r.ms,
      estado: r.data?.estado ?? r.data?.error,
      inscripcionId: r.data?.inscripcionId,
    })),
    pagoSimulado: pago.data,
    cupoDespuesReserva: despuesReserva.cupoDisponible,
    cancelacion: cancelada.data?.estado,
    cupoDespuesCancelacion: despuesCancelacion.cupoDisponible,
  }, null, 2));
} finally {
  if (eventoId) {
    const cleanup = await requestJson('POST', `/api/v1/eventos/${eventoId}/cancelar?motivo=cleanup-demo-concurrencia`, {
      token: adminToken,
    });
    if ([204, 404, 422].includes(cleanup.status)) {
      console.log(`[demo-concurrencia-aws] Cleanup evento status=${cleanup.status}`);
    } else {
      console.warn(`[demo-concurrencia-aws] Cleanup evento fallo HTTP ${cleanup.status}: ${JSON.stringify(cleanup.data)}`);
    }
  }
}
