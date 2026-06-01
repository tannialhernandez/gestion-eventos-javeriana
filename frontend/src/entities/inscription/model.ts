export type Inscription = {
  inscripcionId: string;
  eventoId: string;
  estado: 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'CANCELADA' | 'EXPIRADA' | string;
  fechaInscripcion: string;
  fechaExpiracionPago: string | null;
  checkoutUrl: string | null;
  expiraEnSegundos: number;
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
