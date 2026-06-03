import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ContextualError } from '../../components';
import type { AcademicEvent } from '../../entities/event';
import { listEvents, type EventFilters } from '../../services/eventService';
import { type AppError, normalizeAppError } from '../../lib/errors';
import { formatDate, sanitizeText } from '../../shared/lib';
import { Icon, Skeleton, StatusBadge } from '../../shared/ui';

const tipos = ['', 'CONGRESO', 'SIMPOSIO', 'SEMINARIO', 'TALLER'];
const modalidades = ['', 'PRESENCIAL', 'VIRTUAL', 'HIBRIDO'];

export function CatalogPage() {
  const navigate = useNavigate();
  const [events, setEvents] = useState<AcademicEvent[]>([]);
  const [filters, setFilters] = useState<EventFilters>({ conCupos: true });
  const [search, setSearch] = useState('');
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<AppError | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  const activeFilters = useMemo<EventFilters>(
    () => ({
      ...filters,
      buscar: search.trim() || undefined,
    }),
    [filters, search],
  );

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    listEvents(activeFilters)
      .then((response) => {
        if (mounted) setEvents(response);
      })
      .catch((err) => {
        if (mounted) setError(normalizeAppError(err));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [activeFilters, retryVersion]);

  return (
    <section className="page-grid">
      <div className="page-title">
        <div>
          <span className="eyebrow">Catálogo público</span>
          <h1>Eventos disponibles</h1>
        </div>
        <div className="service-strip" aria-label="Servicios conectados">
          <span>event-service</span>
          <span>Redis cache</span>
          <span>JWT activo</span>
        </div>
      </div>

      <div className="toolbar">
        <input
          className="input"
          aria-label="Buscar eventos"
          placeholder="Buscar por título o descripción"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          className="select"
          aria-label="Filtrar por tipo de evento"
          value={filters.tipo ?? ''}
          onChange={(event) => setFilters((current) => ({ ...current, tipo: event.target.value || undefined }))}
        >
          {tipos.map((tipo) => (
            <option key={tipo || 'todos'} value={tipo}>
              {tipo || 'Todos los tipos'}
            </option>
          ))}
        </select>
        <select
          className="select"
          aria-label="Filtrar por modalidad"
          value={filters.modalidad ?? ''}
          onChange={(event) => setFilters((current) => ({ ...current, modalidad: event.target.value || undefined }))}
        >
          {modalidades.map((modalidad) => (
            <option key={modalidad || 'todas'} value={modalidad}>
              {modalidad || 'Todas las modalidades'}
            </option>
          ))}
        </select>
      </div>

      {error && <ContextualError error={error} onRetry={() => setRetryVersion((current) => current + 1)} />}
      {isLoading && <Skeleton rows={6} />}

      {!isLoading && !error && (
        <div className="event-grid">
          {events.map((event) => (
            <article className="event-card" key={event.id}>
              <div className="event-card__top">
                <span className="event-card__type">{event.tipo}</span>
                <StatusBadge value={event.estado} />
              </div>
              <h2>{sanitizeText(event.titulo)}</h2>
              <p>{sanitizeText(event.descripcion)}</p>
              <dl className="event-card__meta">
                <div>
                  <dt>Inicio</dt>
                  <dd>
                    <Icon name="calendar" size={15} />
                    {formatDate(event.fechaInicio)}
                  </dd>
                </div>
                <div>
                  <dt>Cupos</dt>
                  <dd>
                    <Icon name="ticket" size={15} />
                    {event.cupoDisponible}/{event.cupoMaximo}
                  </dd>
                </div>
              </dl>
              <button className="button button--secondary button--wide" type="button" onClick={() => navigate(`/eventos/${event.id}`)}>
                Ver detalle
              </button>
            </article>
          ))}
        </div>
      )}

      {!isLoading && !error && events.length === 0 && <div className="empty-state">No hay eventos publicados para los filtros seleccionados.</div>}
    </section>
  );
}
