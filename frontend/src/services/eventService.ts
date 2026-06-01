import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import type { AcademicEvent, Tariff } from '../entities/event';
import { withRetry } from '../lib/retry';

const eventHttp = createHttpClient(env.eventApiUrl, 'event-service');

export type EventFilters = {
  tipo?: string;
  modalidad?: string;
  buscar?: string;
  conCupos?: boolean;
};

export async function listEvents(filters: EventFilters): Promise<AcademicEvent[]> {
  return withRetry(async () => {
    const response = await eventHttp.get<AcademicEvent[]>('/eventos', {
      params: {
        ...filters,
        pagina: 0,
        tamano: 30,
      },
    });
    return response.data;
  });
}

export async function getEvent(eventId: string): Promise<AcademicEvent> {
  return withRetry(async () => {
    const response = await eventHttp.get<AcademicEvent>(`/eventos/${eventId}`);
    return response.data;
  });
}

export async function listTariffs(eventId: string): Promise<Tariff[]> {
  return withRetry(async () => {
    const response = await eventHttp.get<Tariff[]>('/tarifas', { params: { eventoId: eventId } });
    return response.data;
  });
}
