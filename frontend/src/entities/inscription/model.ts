export type Inscription = {
  inscripcionId: string;
  eventoId: string;
  tarifaId?: string;
  estado: 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'CANCELADA' | 'EXPIRADA' | string;
  fechaInscripcion: string;
  fechaExpiracionPago: string | null;
  checkoutUrl: string | null;
  expiraEnSegundos: number;
};

export type AttendanceRecord = {
  inscripcionId: string;
  eventoId: string;
  usuarioId: string;
  participante: string;
  estado: string;
  asistio: boolean;
  fechaRegistro: string | null;
  registradoPor: string | null;
  observaciones: string | null;
};

export type CheckoutSnapshot = {
  eventTitle: string;
  eventId: string;
  tariffId: string;
  amount: number;
  currency: string;
  checkoutUrl: string | null;
  expiresAt: string | null;
};
