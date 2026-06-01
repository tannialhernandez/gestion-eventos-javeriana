import http from 'k6/http';

export const EVENT_URL = __ENV.EVENT_URL || 'http://localhost:8082';
export const INSCRIPTION_URL = __ENV.INSCRIPTION_URL || __ENV.BASE_URL || 'http://localhost:8083';
export const PAYMENT_URL = __ENV.PAYMENT_URL || 'http://localhost:8084';

export function padded(value) {
  return String(value).padStart(12, '0');
}

export function eventoId(numero) {
  return `00000000-0000-0000-0000-${padded(numero)}`;
}

export function tarifaId(numero) {
  return `00000000-0000-0000-0001-${padded(numero)}`;
}

export function randomEventoNumber() {
  let value = 1 + Math.floor(Math.random() * 100);
  if (value === 10) value = 11;
  return value;
}

export function randomBetween(minSeconds, maxSeconds) {
  return minSeconds + (Math.random() * (maxSeconds - minSeconds));
}

export function randomEventoId() {
  return eventoId(randomEventoNumber());
}

export function randomTarifaId(eventNumber = null) {
  return tarifaId(eventNumber ?? randomEventoNumber());
}

export function uuidv4() {
  const hex = [];
  for (let i = 0; i < 32; i += 1) {
    hex.push(Math.floor(Math.random() * 16).toString(16));
  }
  hex[12] = '4';
  hex[16] = (8 + Math.floor(Math.random() * 4)).toString(16);
  return `${hex.slice(0, 8).join('')}-${hex.slice(8, 12).join('')}-${hex.slice(12, 16).join('')}-${hex.slice(16, 20).join('')}-${hex.slice(20).join('')}`;
}

export function bodyInscripcion(eventId, tariffId) {
  return JSON.stringify({
    eventoId: eventId,
    tarifaId: tariffId,
    idempotencyKey: uuidv4(),
  });
}

export function postInscripcion(eventId, tariffId, headers, tags = {}) {
  return http.post(
    `${INSCRIPTION_URL}/api/v1/inscripciones`,
    bodyInscripcion(eventId, tariffId),
    {
      headers,
      tags,
      timeout: __ENV.HTTP_TIMEOUT || '5s',
      responseCallback: http.expectedStatuses(200, 201, 409, 422, 503),
    },
  );
}
