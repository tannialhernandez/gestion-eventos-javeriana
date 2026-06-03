const alb = process.env.AWS_ALB ?? 'eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com';
const baseUrl = `http://${alb}/api/v1`;
const email = process.env.AWS_CRUD_EMAIL ?? 'ana.admin@javeriana.edu.co';
const password = process.env.AWS_CRUD_PASSWORD ?? 'demo123';
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  });
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { response, body };
}

function assertOk(label, result, expected) {
  if (!expected.includes(result.response.status)) {
    throw new Error(`${label} fallo. HTTP ${result.response.status}. Body=${JSON.stringify(result.body)}`);
  }
}

const login = await request('/auth/login', {
  method: 'POST',
  body: JSON.stringify({ email, password }),
});
assertOk('login', login, [200]);

const token = login.body.accessToken;
const authHeaders = { Authorization: `Bearer ${token}` };
let eventId = null;

try {
  const createPayload = {
    titulo: `Smoke CRUD AWS ${stamp}`,
    descripcion: 'Evento temporal para validar POST PUT DELETE desde smoke automatizado.',
    tipo: 'CONGRESO',
    modalidad: 'HIBRIDO',
    fechaInicio: '2033-10-10',
    fechaFin: '2033-10-11',
    fechaLimiteInscripcion: '2033-10-01T23:59:00',
    cupoMaximo: 42,
  };

  const created = await request('/eventos', {
    method: 'POST',
    headers: authHeaders,
    body: JSON.stringify(createPayload),
  });
  assertOk('POST /eventos', created, [201]);
  eventId = created.body.id;
  console.log(`[smoke-crud-eventos-aws] POST OK evento=${eventId}`);

  const updatePayload = {
    ...createPayload,
    titulo: `Smoke CRUD AWS editado ${stamp}`,
    cupoMaximo: 45,
  };
  const updated = await request(`/eventos/${eventId}`, {
    method: 'PUT',
    headers: authHeaders,
    body: JSON.stringify(updatePayload),
  });
  assertOk('PUT /eventos/:id', updated, [200]);
  console.log(`[smoke-crud-eventos-aws] PUT OK titulo="${updated.body.titulo}" cupoMaximo=${updated.body.cupoMaximo}`);

  const deleted = await request(`/eventos/${eventId}`, {
    method: 'DELETE',
    headers: authHeaders,
  });
  assertOk('DELETE /eventos/:id', deleted, [204]);
  console.log(`[smoke-crud-eventos-aws] DELETE OK evento=${eventId}`);
  eventId = null;
  console.log('[smoke-crud-eventos-aws] CRUD AWS OK');
} finally {
  if (eventId) {
    const cleanup = await request(`/eventos/${eventId}/cancelar?motivo=cleanup-smoke-crud`, {
      method: 'POST',
      headers: authHeaders,
    });
    if ([204, 404, 422].includes(cleanup.response.status)) {
      console.log(`[smoke-crud-eventos-aws] cleanup status=${cleanup.response.status} evento=${eventId}`);
    } else {
      console.warn(`[smoke-crud-eventos-aws] cleanup fallo status=${cleanup.response.status} body=${JSON.stringify(cleanup.body)}`);
    }
  }
}
