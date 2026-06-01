import type { AxiosError } from 'axios';
import {
  AppError,
  AuthError,
  BusinessRuleError,
  NetworkError,
  ServiceUnavailableError,
  UnexpectedError,
} from './AppError';

type ErrorPayload = Record<string, unknown>;

export function parseRetryAfter(value: unknown): number | undefined {
  const rawValue = Array.isArray(value) ? value[0] : value;
  if (typeof rawValue !== 'string' || rawValue.trim() === '') return undefined;

  const numeric = Number.parseInt(rawValue, 10);
  if (Number.isFinite(numeric) && numeric >= 0) return numeric;

  const dateMs = Date.parse(rawValue);
  if (Number.isNaN(dateMs)) return undefined;
  return Math.max(0, Math.ceil((dateMs - Date.now()) / 1000));
}

function responseMessage(data: unknown, fallback: string): string {
  if (!data || typeof data !== 'object') return fallback;
  const payload = data as ErrorPayload;
  const message = payload.message ?? payload.error_description ?? payload.error;
  return typeof message === 'string' && message.trim() ? message : fallback;
}

function responseCode(data: unknown, fallback: string): string {
  if (!data || typeof data !== 'object') return fallback;
  const payload = data as ErrorPayload;
  const code = payload.error ?? payload.code;
  return typeof code === 'string' && code.trim() ? code : fallback;
}

export function parseHttpError(error: AxiosError<ErrorPayload>, serviceName?: string): AppError {
  if (!error.response) {
    return new NetworkError('No fue posible contactar el servicio. Verifica tu conexion o intenta de nuevo.', error);
  }

  const { status, data, headers } = error.response;
  const retryAfterSeconds = parseRetryAfter(headers['retry-after']);
  const code = responseCode(data, `http_${status}`);

  if (status === 401 || status === 403) {
    return new AuthError(responseMessage(data, 'Tu sesion no esta activa o no tiene permisos para esta accion.'), {
      status,
      code,
      serviceName,
      cause: error,
    });
  }

  if (status === 503) {
    return new ServiceUnavailableError(
      responseMessage(data, 'El servicio esta temporalmente no disponible. Estamos reintentando de forma segura.'),
      {
        status,
        code,
        serviceName,
        retryAfterSeconds,
        cause: error,
      },
    );
  }

  if (status >= 400 && status < 500) {
    return new BusinessRuleError(responseMessage(data, 'La solicitud no se pudo completar.'), {
      status,
      code,
      serviceName,
      cause: error,
    });
  }

  return new UnexpectedError(responseMessage(data, 'El servidor respondio con un error inesperado.'), error);
}
