type StatusBadgeProps = {
  value: string;
};

const labels: Record<string, string> = {
  BORRADOR: 'Borrador',
  PENDIENTE_PUBLICACION: 'Pendiente de aprobación',
  RECHAZADO: 'Rechazado',
  PUBLICADO: 'Publicado',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
  PENDIENTE_PAGO: 'Pendiente pago',
  CONFIRMADA: 'Confirmada',
  CANCELADA: 'Cancelada',
};

export function StatusBadge({ value }: StatusBadgeProps) {
  return <span className={`status-badge status-badge--${value.toLowerCase()}`}>{labels[value] ?? value}</span>;
}
