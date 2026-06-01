import { describe, expect, it, vi } from 'vitest';
import { AuthError, BusinessRuleError, NetworkError, ServiceUnavailableError } from '../errors';
import { withRetry } from './withRetry';

describe('withRetry', () => {
  it('debe reintentar con backoff exponencial', async () => {
    vi.useFakeTimers();
    const operation = vi.fn()
      .mockRejectedValueOnce(new NetworkError())
      .mockResolvedValueOnce('ok');

    const result = withRetry(operation, { retries: 1, baseDelayMs: 20 });
    await vi.advanceTimersByTimeAsync(20);

    await expect(result).resolves.toBe('ok');
    expect(operation).toHaveBeenCalledTimes(2);
  });

  it('debe respetar Retry-After del header', async () => {
    vi.useFakeTimers();
    const operation = vi.fn()
      .mockRejectedValueOnce(new ServiceUnavailableError('Circuit open', { retryAfterSeconds: 2 }))
      .mockResolvedValueOnce('ok');

    const result = withRetry(operation, { retries: 1, baseDelayMs: 20 });
    await vi.advanceTimersByTimeAsync(1999);
    expect(operation).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);

    await expect(result).resolves.toBe('ok');
    expect(operation).toHaveBeenCalledTimes(2);
  });

  it('debe lanzar tras maxAttempts', async () => {
    vi.useFakeTimers();
    const error = new NetworkError();
    const operation = vi.fn().mockRejectedValue(error);

    const result = withRetry(operation, { retries: 2, baseDelayMs: 10 });
    const assertion = expect(result).rejects.toBe(error);
    await vi.advanceTimersByTimeAsync(10);
    await vi.advanceTimersByTimeAsync(20);

    await assertion;
    expect(operation).toHaveBeenCalledTimes(3);
  });

  it('debe no reintentar errores 4xx de negocio', async () => {
    const operation = vi.fn().mockRejectedValue(new BusinessRuleError('Sin cupos', { status: 409 }));

    await expect(withRetry(operation)).rejects.toBeInstanceOf(BusinessRuleError);
    expect(operation).toHaveBeenCalledTimes(1);
  });

  it('debe no reintentar errores de autenticacion', async () => {
    const operation = vi.fn().mockRejectedValue(new AuthError());

    await expect(withRetry(operation)).rejects.toBeInstanceOf(AuthError);
    expect(operation).toHaveBeenCalledTimes(1);
  });
});
