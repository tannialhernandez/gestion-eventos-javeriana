import { useEffect, useMemo, useState, type FormEvent } from 'react';
import type { AcademicEvent, EventMode, EventStatus, EventType, Tariff } from '../../entities/event';
import type { EventMutationInput } from '../../services/eventService';
import { Icon } from '../../shared/ui';

type EventoFormValues = EventMutationInput;

type EventoFormErrors = Partial<Record<keyof EventoFormValues, string>>;

type EventoFormProps = {
  initialEvent?: AcademicEvent | null;
  initialTariff?: Tariff | null;
  submitLabel: string;
  isSubmitting?: boolean;
  onSubmit: (values: EventMutationInput) => Promise<void> | void;
};

const tipos: EventType[] = ['CONGRESO', 'SIMPOSIO', 'SEMINARIO', 'TALLER', 'OTRO'];
const modalidades: EventMode[] = ['PRESENCIAL', 'VIRTUAL', 'HIBRIDO'];
const baseEstados: Array<Extract<EventStatus, 'BORRADOR' | 'PENDIENTE_PUBLICACION'>> = ['BORRADOR', 'PENDIENTE_PUBLICACION'];
const estadoLabels: Record<Extract<EventStatus, 'BORRADOR' | 'PENDIENTE_PUBLICACION' | 'PUBLICADO'>, string> = {
  BORRADOR: 'Guardar como borrador',
  PENDIENTE_PUBLICACION: 'Enviar a aprobación',
  PUBLICADO: 'Mantener publicado',
};

function dateOnly(value: string | undefined): string {
  if (!value) return '';
  return value.split('T')[0] ?? value;
}

function datetimeLocal(value: string | undefined): string {
  if (!value) return '';
  return value.slice(0, 16);
}

function defaultDeadline(fechaInicio: string): string {
  if (!fechaInicio) return '';
  const date = new Date(`${fechaInicio}T12:00:00`);
  date.setDate(date.getDate() - 1);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}T23:59`;
}

function valuesFromEvent(event?: AcademicEvent | null, tariff?: Tariff | null): EventoFormValues {
  const fechaInicio = dateOnly(event?.fechaInicio);
  return {
    tarifaId: tariff?.id,
    titulo: event?.titulo ?? '',
    descripcion: event?.descripcion ?? '',
    tipo: event?.tipo ?? 'CONGRESO',
    modalidad: event?.modalidad === 'HIBRIDA' ? 'HIBRIDO' : (event?.modalidad ?? 'PRESENCIAL'),
    fechaInicio,
    fechaFin: dateOnly(event?.fechaFin),
    fechaLimiteInscripcion: datetimeLocal(event?.fechaLimiteInscripcion) || defaultDeadline(fechaInicio),
    cupoMaximo: event?.cupoMaximo ?? 50,
    lugar: 'Pontificia Universidad Javeriana - Sede Bogotá',
    tarifaMonto: tariff?.monto ?? 150000,
    tarifaMoneda: (tariff?.moneda === 'USD' ? 'USD' : 'COP'),
    estado: event?.estado === 'PUBLICADO'
      ? 'PUBLICADO'
      : event?.estado === 'PENDIENTE_PUBLICACION'
        ? 'PENDIENTE_PUBLICACION'
        : 'BORRADOR',
  };
}

function validate(values: EventoFormValues): EventoFormErrors {
  const errors: EventoFormErrors = {};
  if (!values.titulo.trim()) errors.titulo = 'El título es obligatorio.';
  if (values.titulo.length > 200) errors.titulo = 'El título no puede superar 200 caracteres.';
  if (!values.descripcion.trim()) errors.descripcion = 'La descripción es obligatoria.';
  if (!values.fechaInicio) errors.fechaInicio = 'La fecha de inicio es obligatoria.';
  if (!values.fechaFin) errors.fechaFin = 'La fecha de fin es obligatoria.';
  if (values.fechaInicio && values.fechaFin && values.fechaFin < values.fechaInicio) {
    errors.fechaFin = 'La fecha de fin debe ser posterior al inicio.';
  }
  if (!values.fechaLimiteInscripcion) errors.fechaLimiteInscripcion = 'La fecha límite de inscripción es obligatoria.';
  if (values.fechaInicio && values.fechaLimiteInscripcion && values.fechaLimiteInscripcion.slice(0, 10) >= values.fechaInicio) {
    errors.fechaLimiteInscripcion = 'La fecha límite debe ser anterior al inicio.';
  }
  if (!values.lugar.trim()) errors.lugar = 'El lugar es obligatorio.';
  if (!Number.isFinite(values.cupoMaximo) || values.cupoMaximo < 1 || values.cupoMaximo > 1000) {
    errors.cupoMaximo = 'La capacidad debe estar entre 1 y 1000.';
  }
  if (!Number.isFinite(values.tarifaMonto) || values.tarifaMonto < 0) {
    errors.tarifaMonto = 'El monto de la tarifa debe ser cero o mayor.';
  }
  return errors;
}

export function EventoForm({ initialEvent = null, initialTariff = null, submitLabel, isSubmitting = false, onSubmit }: EventoFormProps) {
  const [values, setValues] = useState<EventoFormValues>(() => valuesFromEvent(initialEvent, initialTariff));
  const [errors, setErrors] = useState<EventoFormErrors>({});
  const estados = useMemo(
    () => initialEvent?.estado === 'PUBLICADO'
      ? [...baseEstados, 'PUBLICADO' as const]
      : baseEstados,
    [initialEvent?.estado],
  );

  useEffect(() => {
    setValues(valuesFromEvent(initialEvent, initialTariff));
    setErrors({});
  }, [initialEvent, initialTariff]);

  const titleCount = useMemo(() => `${values.titulo.length}/200`, [values.titulo.length]);

  const update = <K extends keyof EventoFormValues>(key: K, value: EventoFormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors = validate(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    await onSubmit(values);
  };

  return (
    <form className="event-form" onSubmit={handleSubmit} noValidate>
      <div className="event-form__grid">
        <label className="field">
          <span>Título</span>
          <input
            className="input"
            name="titulo"
            maxLength={200}
            value={values.titulo}
            aria-invalid={Boolean(errors.titulo)}
            aria-describedby={errors.titulo ? 'titulo-help titulo-error' : 'titulo-help'}
            onChange={(event) => update('titulo', event.target.value)}
            required
          />
          <small id="titulo-help">{titleCount}</small>
          {errors.titulo && <span className="form-error" id="titulo-error">{errors.titulo}</span>}
        </label>

        <label className="field">
          <span>Tipo</span>
          <select className="select" value={values.tipo} onChange={(event) => update('tipo', event.target.value as EventType)}>
            {tipos.map((tipo) => <option key={tipo} value={tipo}>{tipo}</option>)}
          </select>
        </label>

        <label className="field event-form__wide">
          <span>Descripción</span>
          <textarea
            className="textarea"
            name="descripcion"
            value={values.descripcion}
            aria-invalid={Boolean(errors.descripcion)}
            aria-describedby={errors.descripcion ? 'descripcion-error' : undefined}
            onChange={(event) => update('descripcion', event.target.value)}
            required
          />
          {errors.descripcion && <span className="form-error" id="descripcion-error">{errors.descripcion}</span>}
        </label>

        <label className="field">
          <span>Fecha inicio</span>
          <input
            className="input"
            name="fechaInicio"
            type="date"
            value={values.fechaInicio}
            aria-invalid={Boolean(errors.fechaInicio)}
            aria-describedby={errors.fechaInicio ? 'fechaInicio-error' : undefined}
            onChange={(event) => {
              const nextStart = event.target.value;
              update('fechaInicio', nextStart);
              if (!values.fechaLimiteInscripcion) update('fechaLimiteInscripcion', defaultDeadline(nextStart));
            }}
            required
          />
          {errors.fechaInicio && <span className="form-error" id="fechaInicio-error">{errors.fechaInicio}</span>}
        </label>

        <label className="field">
          <span>Fecha fin</span>
          <input
            className="input"
            name="fechaFin"
            type="date"
            value={values.fechaFin}
            aria-invalid={Boolean(errors.fechaFin)}
            aria-describedby={errors.fechaFin ? 'fechaFin-error' : undefined}
            onChange={(event) => update('fechaFin', event.target.value)}
            required
          />
          {errors.fechaFin && <span className="form-error" id="fechaFin-error">{errors.fechaFin}</span>}
        </label>

        <label className="field">
          <span>Fecha límite de inscripción</span>
          <input
            className="input"
            name="fechaLimiteInscripcion"
            type="datetime-local"
            value={values.fechaLimiteInscripcion}
            aria-invalid={Boolean(errors.fechaLimiteInscripcion)}
            aria-describedby={errors.fechaLimiteInscripcion ? 'fechaLimiteInscripcion-error' : undefined}
            onChange={(event) => update('fechaLimiteInscripcion', event.target.value)}
            required
          />
          {errors.fechaLimiteInscripcion && <span className="form-error" id="fechaLimiteInscripcion-error">{errors.fechaLimiteInscripcion}</span>}
        </label>

        <label className="field">
          <span>Lugar</span>
          <input
            className="input"
            name="lugar"
            value={values.lugar}
            aria-invalid={Boolean(errors.lugar)}
            aria-describedby={errors.lugar ? 'lugar-error' : undefined}
            onChange={(event) => update('lugar', event.target.value)}
            required
          />
          {errors.lugar && <span className="form-error" id="lugar-error">{errors.lugar}</span>}
        </label>

        <label className="field">
          <span>Modalidad</span>
          <select className="select" value={values.modalidad} onChange={(event) => update('modalidad', event.target.value as EventMode)}>
            {modalidades.map((modalidad) => <option key={modalidad} value={modalidad}>{modalidad}</option>)}
          </select>
        </label>

        <label className="field">
          <span>Capacidad</span>
          <input
            className="input"
            name="cupoMaximo"
            type="number"
            min={1}
            max={1000}
            value={values.cupoMaximo}
            aria-invalid={Boolean(errors.cupoMaximo)}
            aria-describedby={errors.cupoMaximo ? 'cupoMaximo-error' : undefined}
            onChange={(event) => update('cupoMaximo', Number(event.target.value))}
            required
          />
          {errors.cupoMaximo && <span className="form-error" id="cupoMaximo-error">{errors.cupoMaximo}</span>}
        </label>

        <label className="field">
          <span>Tarifa monto</span>
          <input
            className="input"
            name="tarifaMonto"
            type="number"
            min={0}
            value={values.tarifaMonto}
            aria-invalid={Boolean(errors.tarifaMonto)}
            aria-describedby={errors.tarifaMonto ? 'tarifaMonto-error' : undefined}
            onChange={(event) => update('tarifaMonto', Number(event.target.value))}
            required
          />
          {errors.tarifaMonto && <span className="form-error" id="tarifaMonto-error">{errors.tarifaMonto}</span>}
        </label>

        <label className="field">
          <span>Tarifa moneda</span>
          <select className="select" value={values.tarifaMoneda} onChange={(event) => update('tarifaMoneda', event.target.value as 'COP' | 'USD')}>
            <option value="COP">COP</option>
            <option value="USD">USD</option>
          </select>
        </label>

        <label className="field">
          <span>Estado</span>
          <select className="select" value={values.estado} onChange={(event) => update('estado', event.target.value as EventoFormValues['estado'])}>
            {estados.map((estado) => <option key={estado} value={estado}>{estadoLabels[estado]}</option>)}
          </select>
        </label>
      </div>

      <button className="button button--primary event-form__submit" type="submit" disabled={isSubmitting}>
        <Icon name="check" />
        {isSubmitting ? 'Guardando evento' : submitLabel}
      </button>
    </form>
  );
}
