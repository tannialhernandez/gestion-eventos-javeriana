export type AppErrorKind =
  | 'service_unavailable'
  | 'network'
  | 'business_rule'
  | 'auth'
  | 'unexpected';

type AppErrorOptions = {
  kind: AppErrorKind;
  message: string;
  status?: number;
  code?: string;
  serviceName?: string;
  retryAfterSeconds?: number;
  cause?: unknown;
};

export class AppError extends Error {
  readonly kind: AppErrorKind;
  readonly status: number;
  readonly code: string;
  readonly serviceName?: string;
  readonly retryAfterSeconds?: number;
  readonly cause?: unknown;

  constructor(options: AppErrorOptions) {
    super(options.message);
    this.name = 'AppError';
    this.kind = options.kind;
    this.status = options.status ?? 0;
    this.code = options.code ?? options.kind;
    this.serviceName = options.serviceName;
    this.retryAfterSeconds = options.retryAfterSeconds;
    this.cause = options.cause;
  }
}

export class ServiceUnavailableError extends AppError {
  constructor(message: string, options: Omit<AppErrorOptions, 'kind' | 'message'> = {}) {
    super({ ...options, kind: 'service_unavailable', message, status: options.status ?? 503 });
    this.name = 'ServiceUnavailableError';
  }
}

export class NetworkError extends AppError {
  constructor(message = 'No hay conexion con el servicio. Revisa tu red e intenta nuevamente.', cause?: unknown) {
    super({ kind: 'network', message, code: 'network_error', cause });
    this.name = 'NetworkError';
  }
}

export class BusinessRuleError extends AppError {
  constructor(message: string, options: Omit<AppErrorOptions, 'kind' | 'message'> = {}) {
    super({ ...options, kind: 'business_rule', message });
    this.name = 'BusinessRuleError';
  }
}

export class AuthError extends AppError {
  constructor(message = 'Tu sesion expiro. Inicia sesion nuevamente.', options: Omit<AppErrorOptions, 'kind' | 'message'> = {}) {
    super({ ...options, kind: 'auth', message, status: options.status ?? 401, code: options.code ?? 'auth_error' });
    this.name = 'AuthError';
  }
}

export class UnexpectedError extends AppError {
  constructor(message = 'Ocurrio un error inesperado.', cause?: unknown) {
    super({ kind: 'unexpected', message, code: 'unexpected_error', cause });
    this.name = 'UnexpectedError';
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}

export function normalizeAppError(error: unknown): AppError {
  if (isAppError(error)) return error;
  if (error instanceof Error) return new UnexpectedError(error.message, error);
  return new UnexpectedError();
}
