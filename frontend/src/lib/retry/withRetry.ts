import { NetworkError, ServiceUnavailableError, type AppError } from '../errors';

type RetryOptions = {
  retries?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
};

const defaultOptions: Required<RetryOptions> = {
  retries: 1,
  baseDelayMs: 600,
  maxDelayMs: 5000,
};

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function retryDelay(error: AppError, attempt: number, options: Required<RetryOptions>): number {
  if (error instanceof ServiceUnavailableError && error.retryAfterSeconds !== undefined) {
    return error.retryAfterSeconds * 1000;
  }
  return Math.min(options.maxDelayMs, options.baseDelayMs * 2 ** attempt);
}

function shouldRetry(error: AppError, attempt: number, options: Required<RetryOptions>): boolean {
  if (attempt >= options.retries) return false;
  return error instanceof ServiceUnavailableError || error instanceof NetworkError;
}

export async function withRetry<T>(operation: () => Promise<T>, retryOptions: RetryOptions = {}): Promise<T> {
  const options = { ...defaultOptions, ...retryOptions };
  let attempt = 0;

  for (;;) {
    try {
      return await operation();
    } catch (error) {
      const appError = error as AppError;
      if (!shouldRetry(appError, attempt, options)) throw error;
      await delay(retryDelay(appError, attempt, options));
      attempt += 1;
    }
  }
}
