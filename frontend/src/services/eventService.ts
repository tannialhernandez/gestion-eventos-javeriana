import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import type { AcademicEvent, EventMode, EventStatus, EventType, Tariff } from '../entities/event';
import { withRetry } from '../lib/retry';
import { BusinessRuleError, isAppError } from '../lib/errors';

const eventHttp = createHttpClient(env.eventApiUrl, 'event-service');

export type EventFilters = {
  tipo?: string;
  modalidad?: string;
  buscar?: string;
  conCupos?: boolean;
  estado?: EventStatus;
  incluirPropios?: boolean;
};

export type EventMutationInput = {
  tarifaId?: string;
  titulo: string;
  descripcion: string;
  tipo: EventType;
  modalidad: EventMode;
  fechaInicio: string;
  fechaFin: string;
  fechaLimiteInscripcion: string;
  cupoMaximo: number;
  lugar: string;
  tarifaMonto: number;
  tarifaMoneda: 'COP' | 'USD';
  estado: Extract<EventStatus, 'BORRADOR' | 'PENDIENTE_PUBLICACION' | 'PUBLICADO'>;
};

type TariffMutationPayload = {
  eventoId?: string;
  descripcion: string;
  monto: number;
  moneda: 'COP' | 'USD';
};

type BackendEventPayload = {
  titulo: string;
  descripcion: string;
  tipo: EventType;
  modalidad: EventMode;
  fechaInicio: string;
  fechaFin: string;
  fechaLimiteInscripcion: string;
  cupoMaximo: number;
  estado: EventMutationInput['estado'];
};

function toBackendPayload(input: EventMutationInput): BackendEventPayload {
  return {
    titulo: input.titulo,
    descripcion: input.descripcion,
    tipo: input.tipo,
    modalidad: input.modalidad,
    fechaInicio: input.fechaInicio,
    fechaFin: input.fechaFin,
    fechaLimiteInscripcion: input.fechaLimiteInscripcion,
    cupoMaximo: input.cupoMaximo,
    estado: input.estado,
  };
}

export async function listEvents(filters: EventFilters): Promise<AcademicEvent[]> {
  return withRetry(async () => {
    const response = await eventHttp.get<AcademicEvent[]>('/eventos', {
      params: {
        ...filters,
        pagina: 0,
        tamano: 30,
        _ts: Date.now(),
      },
    });
    return response.data;
  });
}

export async function getEvent(eventId: string): Promise<AcademicEvent> {
  return withRetry(async () => {
    const response = await eventHttp.get<AcademicEvent>(`/eventos/${eventId}`, { params: { _ts: Date.now() } });
    return response.data;
  });
}

export async function listTariffs(eventId: string): Promise<Tariff[]> {
  return withRetry(async () => {
    const response = await eventHttp.get<Tariff[]>('/tarifas', { params: { eventoId: eventId, _ts: Date.now() } });
    return response.data;
  });
}

async function createTariff(eventId: string, input: EventMutationInput): Promise<Tariff> {
  const payload: TariffMutationPayload = {
    eventoId: eventId,
    descripcion: 'Tarifa General AWS',
    monto: input.tarifaMonto,
    moneda: input.tarifaMoneda,
  };
  const response = await eventHttp.post<Tariff>('/tarifas', payload);
  return response.data;
}

async function updateTariff(tariffId: string, input: EventMutationInput): Promise<Tariff> {
  const payload: TariffMutationPayload = {
    descripcion: 'Tarifa General AWS',
    monto: input.tarifaMonto,
    moneda: input.tarifaMoneda,
  };
  const response = await eventHttp.put<Tariff>(`/tarifas/${tariffId}`, payload);
  return response.data;
}

export async function publishEvent(eventId: string): Promise<void> {
  await eventHttp.post(`/eventos/${eventId}/publicar`);
}

export async function sendEventToReview(eventId: string): Promise<AcademicEvent> {
  const response = await eventHttp.post<AcademicEvent>(`/eventos/${eventId}/enviar-revision`);
  return response.data;
}

export async function approveEvent(eventId: string): Promise<AcademicEvent> {
  const response = await eventHttp.post<AcademicEvent>(`/eventos/${eventId}/aprobar`);
  return response.data;
}

export async function rejectEvent(eventId: string, motivo: string): Promise<AcademicEvent> {
  const response = await eventHttp.post<AcademicEvent>(`/eventos/${eventId}/rechazar`, { motivo });
  return response.data;
}

export async function createEvent(input: EventMutationInput): Promise<AcademicEvent> {
  const response = await eventHttp.post<AcademicEvent>('/eventos', toBackendPayload(input));
  await createTariff(response.data.id, input);
  if (input.estado === 'PENDIENTE_PUBLICACION' || input.estado === 'PUBLICADO') {
    return sendEventToReview(response.data.id);
  }
  return response.data;
}

export async function updateEvent(eventId: string, input: EventMutationInput): Promise<AcademicEvent> {
  try {
    const response = await eventHttp.put<AcademicEvent>(`/eventos/${eventId}`, toBackendPayload(input));
    if (input.tarifaId) {
      await updateTariff(input.tarifaId, input);
    } else {
      await createTariff(eventId, input);
    }
    if ((input.estado === 'PENDIENTE_PUBLICACION' || input.estado === 'PUBLICADO') && response.data.estado !== 'PUBLICADO') {
      return sendEventToReview(eventId);
    }
    return response.data;
  } catch (error) {
    if (isAppError(error) && (error.status === 401 || error.status === 403)) {
      throw new BusinessRuleError(
        'La API productiva rechazó la edición del evento. Tu sesión sigue activa; falta revisar permisos o configuración del event-service.',
        { status: error.status, code: 'event_update_rejected', serviceName: error.serviceName, cause: error },
      );
    }
    throw error;
  }
}

export async function cancelEvent(eventId: string, motivo = 'Cancelado desde gestión de eventos'): Promise<void> {
  await eventHttp.post(`/eventos/${eventId}/cancelar`, null, { params: { motivo } });
}

export async function deleteEvent(eventId: string): Promise<void> {
  try {
    await eventHttp.delete(`/eventos/${eventId}`);
  } catch (error) {
    if (isAppError(error) && (error.status === 404 || error.status === 405)) {
      await cancelEvent(eventId, 'Eliminado desde SPA organizador');
      return;
    }
    throw error;
  }
}
