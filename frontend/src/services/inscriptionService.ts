import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import type { Inscription } from '../entities/inscription';
import { withRetry } from '../lib/retry';

const inscriptionHttp = createHttpClient(env.inscriptionApiUrl, 'inscription-service');

export async function createInscription(eventoId: string, tarifaId: string): Promise<Inscription> {
  const payload = {
    eventoId,
    tarifaId,
    idempotencyKey: crypto.randomUUID(),
  };

  return withRetry(async () => {
    const response = await inscriptionHttp.post<Inscription>('/inscripciones', payload);
    return response.data;
  });
}
