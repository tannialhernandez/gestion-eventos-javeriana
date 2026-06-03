#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://d1xvny1kolb55e.cloudfront.net}"
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_INSTANCE_ID="${AWS_INSTANCE_ID:-i-07e0425d504f41872}"
SEED_AWS_DATA="${SEED_AWS_DATA:-true}"
PAYMENT_WEBHOOK_SECRET="${PAYMENT_WEBHOOK_SECRET:-change-me-before-production}"

export BASE_URL AWS_REGION AWS_INSTANCE_ID SEED_AWS_DATA PAYMENT_WEBHOOK_SECRET

node --input-type=module <<'NODE'
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const baseUrl = process.env.BASE_URL;
const awsRegion = process.env.AWS_REGION;
const awsInstanceId = process.env.AWS_INSTANCE_ID;
const seedAwsData = process.env.SEED_AWS_DATA !== 'false';
const webhookSecret = process.env.PAYMENT_WEBHOOK_SECRET;

let eventoId = process.env.EVENTO_ID ?? crypto.randomUUID();
let tarifaId = process.env.TARIFA_ID ?? crypto.randomUUID();

const users = [
  {
    label: 'ADMIN',
    email: 'ana.admin@javeriana.edu.co',
    password: 'demo123',
    expectedRole: 'ADMIN',
    validatesParticipantFlow: false,
  },
  {
    label: 'ORGANIZADOR',
    email: 'carlos.organizador@javeriana.edu.co',
    password: 'demo123',
    expectedRole: 'ORGANIZADOR',
    validatesParticipantFlow: false,
  },
  {
    label: 'PARTICIPANTE',
    email: 'sofia.soporte@javeriana.edu.co',
    password: 'demo123',
    expectedRole: 'PARTICIPANTE',
    validatesParticipantFlow: true,
  },
];

function fail(message) {
  throw new Error(`[smoke-multi-rol-aws] ${message}`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function runAws(args) {
  return execFileSync('aws', args, { encoding: 'utf8' }).trim();
}

async function seedFreshAwsEvent() {
  if (!seedAwsData) {
    console.log(`[smoke-multi-rol-aws] Seed AWS deshabilitado. Evento=${eventoId} Tarifa=${tarifaId}`);
    return;
  }

  const sql = [
    `INSERT INTO public.evento (id, titulo, descripcion, tipo, modalidad, fecha_inicio, fecha_fin, fecha_limite_inscripcion, cupo_maximo, cupo_disponible, estado, organizador_id, fecha_creacion, version) VALUES ('${eventoId}'::uuid, 'Smoke multi rol AWS', 'Evento efimero para smoke multi-rol productivo.', 'CONGRESO', 'HIBRIDO', CURRENT_DATE + INTERVAL '30 days', CURRENT_DATE + INTERVAL '32 days', (CURRENT_DATE + INTERVAL '25 days')::timestamp, 20, 20, 'PUBLICADO', '33333333-3333-3333-3333-333333333333'::uuid, NOW(), 0) ON CONFLICT (id) DO NOTHING`,
    `INSERT INTO public.tarifa (id, evento_id, nombre, precio, moneda, aplica_a, fecha_inicio_vigencia, fecha_fin_vigencia, activa) VALUES ('${tarifaId}'::uuid, '${eventoId}'::uuid, 'Tarifa smoke AWS', 150000.00, 'COP', 'ESTUDIANTE_JAVERIANA', CURRENT_DATE - INTERVAL '1 day', CURRENT_DATE + INTERVAL '60 days', true) ON CONFLICT (id) DO NOTHING`,
    `INSERT INTO inscription.evento_cupo (evento_id, cupo_disponible, cupo_maximo, version) VALUES ('${eventoId}'::uuid, 20, 20, 0) ON CONFLICT (evento_id) DO NOTHING`,
  ].join('; ');

  const remoteCommand = [
    'set -e',
    'DB_HOST=$(docker exec eventos-event-service-1 printenv DB_HOST)',
    'DB_NAME=$(docker exec eventos-event-service-1 printenv DB_NAME)',
    'DB_USER=$(docker exec eventos-event-service-1 printenv DB_USERNAME)',
    'DB_PASS=$(docker exec eventos-event-service-1 printenv DB_PASSWORD)',
    `docker run --rm --network host -e PGPASSWORD=$DB_PASS postgres:15-alpine psql -h $DB_HOST -U $DB_USER -d $DB_NAME -v ON_ERROR_STOP=1 -c ${JSON.stringify(sql)}`,
    'REDIS_HOST=$(docker exec eventos-event-service-1 printenv REDIS_HOST)',
    `docker run --rm --network host redis:7-alpine redis-cli -h $REDIS_HOST DEL catalogo:publicados evento:${eventoId}`,
  ].join('; ');

  const commandId = runAws([
    'ssm',
    'send-command',
    '--region',
    awsRegion,
    '--instance-ids',
    awsInstanceId,
    '--document-name',
    'AWS-RunShellScript',
    '--parameters',
    JSON.stringify({ commands: [remoteCommand] }),
    '--query',
    'Command.CommandId',
    '--output',
    'text',
  ]);

  for (let attempt = 0; attempt < 45; attempt += 1) {
    const invocation = JSON.parse(runAws([
      'ssm',
      'get-command-invocation',
      '--region',
      awsRegion,
      '--command-id',
      commandId,
      '--instance-id',
      awsInstanceId,
      '--output',
      'json',
    ]));

    if (invocation.Status === 'Success') {
      console.log(`[smoke-multi-rol-aws] Seed AWS OK. Evento=${eventoId} Tarifa=${tarifaId}`);
      return;
    }
    if (['Failed', 'Cancelled', 'TimedOut'].includes(invocation.Status)) {
      fail(`seed AWS fallo: ${invocation.Status}. ${invocation.StandardErrorContent ?? ''}`);
    }
    await sleep(2000);
  }

  fail(`seed AWS no termino a tiempo. CommandId=${commandId}`);
}

function decodeJwt(token) {
  const [, payload] = token.split('.');
  if (!payload) fail('JWT malformado: no tiene payload.');
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
}

async function requestJson(method, path, { body, token, headers } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(headers ?? {}),
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
  return { response, data };
}

async function login(user) {
  const { response, data } = await requestJson('POST', '/api/v1/auth/login', {
    body: { email: user.email, password: user.password },
  });

  if (response.status !== 200) {
    fail(`login ${user.label} retorno HTTP ${response.status}: ${JSON.stringify(data)}`);
  }

  const token = data.accessToken ?? data.access_token;
  if (!token) fail(`login ${user.label} no retorno accessToken.`);

  const claims = decodeJwt(token);
  const roles = Array.isArray(claims.roles) ? claims.roles : [];
  if (!roles.includes(user.expectedRole)) {
    fail(`login ${user.label} no contiene rol ${user.expectedRole}. Roles=${JSON.stringify(roles)}`);
  }

  return { token, claims };
}

async function validateCatalog(user, token) {
  const { response, data } = await requestJson('GET', `/api/v1/eventos?smoke=${Date.now()}`, { token });
  if (response.status !== 200) {
    fail(`catalogo para ${user.label} retorno HTTP ${response.status}: ${JSON.stringify(data)}`);
  }
  if (!Array.isArray(data)) {
    fail(`catalogo para ${user.label} no retorno una lista.`);
  }
  if (!data.some((evento) => evento.id === eventoId && evento.aceptaInscripciones === true)) {
    fail(`catalogo para ${user.label} no contiene el evento publicado esperado ${eventoId}.`);
  }
  return data.length;
}

async function validateParticipantFlow(token) {
  const idempotencyKey = crypto.randomUUID();
  const { response: inscriptionResponse, data: inscription } = await requestJson(
    'POST',
    '/api/v1/inscripciones',
    {
      token,
      body: { eventoId, tarifaId, idempotencyKey },
    },
  );

  if (![200, 201].includes(inscriptionResponse.status)) {
    fail(`crear inscripcion retorno HTTP ${inscriptionResponse.status}: ${JSON.stringify(inscription)}`);
  }
  if (!inscription.inscripcionId || inscription.estado !== 'PENDIENTE_PAGO') {
    fail(`respuesta de inscripcion inesperada: ${JSON.stringify(inscription)}`);
  }

  const webhookBody = JSON.stringify({
    referencia_externa: `AWS-SMOKE-${Date.now()}`,
    inscripcion_id: inscription.inscripcionId,
    estado: 'approved',
    metadata: { origen: 'smoke-multi-rol-aws' },
  });
  const signature = crypto.createHmac('sha256', webhookSecret).update(webhookBody).digest('hex');

  const webhookResponse = await fetch(`${baseUrl}/api/v1/webhooks/pagos`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Signature': signature,
      'X-Correlation-Id': `aws-smoke-${Date.now()}`,
    },
    body: webhookBody,
  });
  const webhookText = await webhookResponse.text();
  const webhook = webhookText ? JSON.parse(webhookText) : {};

  if (webhookResponse.status !== 200 || webhook.resultado !== 'CONFIRMADO') {
    fail(`webhook pago retorno HTTP ${webhookResponse.status}: ${webhookText}`);
  }

  return {
    inscripcionId: inscription.inscripcionId,
    checkoutUrl: inscription.checkoutUrl,
    webhookResultado: webhook.resultado,
  };
}

console.log(`[smoke-multi-rol-aws] Base URL: ${baseUrl}`);
await seedFreshAwsEvent();

const summary = [];
for (const user of users) {
  const { token, claims } = await login(user);
  const catalogItems = await validateCatalog(user, token);
  const row = {
    rol: user.label,
    email: user.email,
    sub: claims.sub,
    catalogoHttp: 200,
    eventosVisibles: catalogItems,
    eventoId,
  };

  if (user.validatesParticipantFlow) {
    Object.assign(row, await validateParticipantFlow(token));
  }

  summary.push(row);
  console.log(`[smoke-multi-rol-aws] OK ${user.label}: login + catalogo${user.validatesParticipantFlow ? ' + inscripcion + pago' : ''}`);
}

console.log('[smoke-multi-rol-aws] Resumen:');
console.log(JSON.stringify(summary, null, 2));
NODE
