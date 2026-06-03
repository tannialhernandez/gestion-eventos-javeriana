export type EventType = 'CONGRESO' | 'SIMPOSIO' | 'SEMINARIO' | 'TALLER' | 'OTRO';
export type EventMode = 'PRESENCIAL' | 'VIRTUAL' | 'HIBRIDA' | 'HIBRIDO';
export type EventStatus = 'BORRADOR' | 'PENDIENTE_PUBLICACION' | 'RECHAZADO' | 'PUBLICADO' | 'FINALIZADO' | 'CANCELADO';

export type AcademicEvent = {
  id: string;
  titulo: string;
  descripcion: string;
  tipo: EventType;
  modalidad: EventMode;
  fechaInicio: string;
  fechaFin: string;
  fechaLimiteInscripcion: string;
  cupoMaximo: number;
  cupoDisponible: number;
  urlImagen?: string | null;
  estado: EventStatus;
  organizadorId: string;
  aceptaInscripciones: boolean;
};

export type Tariff = {
  id: string;
  monto: number;
  moneda: string;
  descripcion: string;
};
