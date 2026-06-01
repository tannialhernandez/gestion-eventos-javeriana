import crypto from 'node:crypto'

const appBaseUrl = process.env.SPA_BASE_URL ?? 'http://127.0.0.1:3000'
const webhookSecret = process.env.VITE_PAYMENT_WEBHOOK_SECRET ?? 'local-demo-secret'

function hmac(body) {
  return crypto.createHmac('sha256', webhookSecret).update(body).digest('hex')
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${url} -> ${response.status}: ${text}`)
  }
  return { response, data }
}

async function requestJsonRaw(url, options = {}) {
  const response = await fetch(url, options)
  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { raw: text }
    }
  }
  return { response, data, text }
}

const { data: login } = await requestJson(`${appBaseUrl}/auth-api/api/v1/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Correlation-Id': `spa-smoke-auth-${Date.now()}`,
  },
  body: JSON.stringify({
    email: 'diego.participante@javeriana.edu.co',
    password: 'demo123',
  }),
})

if (!login?.accessToken) {
  throw new Error('auth-service-stub no retorno accessToken')
}

const token = login.accessToken
const headers = {
  Authorization: `Bearer ${token}`,
  'Content-Type': 'application/json',
  'X-Correlation-Id': `spa-smoke-${Date.now()}`,
}

const { data: events } = await requestJson(`${appBaseUrl}/event-api/api/v1/eventos?pagina=0&tamano=30&conCupos=true`)
if (!Array.isArray(events) || events.length === 0) {
  throw new Error('El catalogo no retorno eventos publicados')
}

let event
let tariff
let inscriptionResponse
let inscription
let lastInscriptionError = ''

const startIndex = Date.now() % events.length
for (let offset = 0; offset < events.length; offset += 1) {
  event = events[(startIndex + offset) % events.length]
  const { data: tariffs } = await requestJson(`${appBaseUrl}/event-api/api/v1/tarifas?eventoId=${event.id}`)
  if (!Array.isArray(tariffs) || tariffs.length === 0) continue
  tariff = tariffs[0]

  const result = await requestJsonRaw(`${appBaseUrl}/inscription-api/api/v1/inscripciones`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      eventoId: event.id,
      tarifaId: tariff.id,
      idempotencyKey: crypto.randomUUID(),
    }),
  })

  inscriptionResponse = result.response
  inscription = result.data

  if ([200, 201].includes(inscriptionResponse.status) && inscription?.inscripcionId) {
    break
  }

  lastInscriptionError = `${inscriptionResponse.status}: ${result.text}`
  if (inscriptionResponse.status === 401 || inscriptionResponse.status === 403) {
    throw new Error(`Token emitido por auth-service-stub fue rechazado: ${lastInscriptionError}`)
  }
}

if (!inscriptionResponse || ![200, 201].includes(inscriptionResponse.status) || !inscription?.inscripcionId) {
  throw new Error(`No se pudo crear inscripcion con ninguno de los eventos probados. Ultimo error: ${lastInscriptionError}`)
}

const webhookBody = JSON.stringify({
  referencia_externa: `SPA-SMOKE-${Date.now()}`,
  inscripcion_id: inscription.inscripcionId,
  estado: 'approved',
  metadata: { origen: 'frontend-spa-smoke' },
})

const { data: webhookResult } = await requestJson(`${appBaseUrl}/payment-api/api/v1/webhooks/pagos`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Correlation-Id': `spa-smoke-payment-${Date.now()}`,
    'X-Signature': hmac(webhookBody),
  },
  body: webhookBody,
})

console.log(
  JSON.stringify(
    {
      login: login.user?.email,
      catalogo: event.id,
      tarifa: tariff.id,
      inscripcion: inscription.inscripcionId,
      pago: webhookResult,
    },
    null,
    2,
  ),
)
