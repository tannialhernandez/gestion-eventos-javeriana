import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import type { Inscription } from '../entities/inscription';
import { withRetry } from '../lib/retry';
import { BusinessRuleError, isAppError } from '../lib/errors';

const inscriptionHttp = createHttpClient(env.inscriptionApiUrl, 'inscription-service');

export async function createInscription(eventoId: string, tarifaId: string): Promise<Inscription> {
  const payload = {
    eventoId,
    tarifaId,
    idempotencyKey: crypto.randomUUID(),
  };

  try {
    return await withRetry(async () => {
      const response = await inscriptionHttp.post<Inscription>('/inscripciones', payload);
      return response.data;
    });
  } catch (error) {
    if (isAppError(error) && (error.status === 401 || error.status === 403)) {
      throw new BusinessRuleError(
        'La API productiva rechazó la inscripción. Tu sesión sigue activa; revisa permisos del participante o configuración del inscription-service.',
        { status: error.status, code: 'inscription_rejected', serviceName: error.serviceName, cause: error },
      );
    }
    throw error;
  }
}

export async function getMyInscriptionForEvent(eventoId: string): Promise<Inscription | null> {
  try {
    const response = await inscriptionHttp.get<Inscription>('/inscripciones/mia', {
      params: { eventoId },
    });
    return response.data;
  } catch (error) {
    if (isAppError(error) && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export async function listMyInscriptions(): Promise<Inscription[]> {
  const response = await inscriptionHttp.get<Inscription[]>('/inscripciones/mias');
  return response.data;
}

export async function cancelInscription(inscripcionId: string): Promise<Inscription> {
  const response = await inscriptionHttp.post<Inscription>(`/inscripciones/${inscripcionId}/cancelar`);
  return response.data;
}
