import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ContextualError } from '../../components';
import type { AcademicEvent } from '../../entities/event';
import type { Inscription } from '../../entities/inscription';
import { useAuth } from '../auth';
import { canCreateEvents, canManageEvent, canManageEvents, getRolePresentation } from '../auth/rolePresentation';
import { listEvents, type EventFilters } from '../../services/eventService';
import { listMyInscriptions } from '../../services/inscriptionService';
import { type AppError, normalizeAppError } from '../../lib/errors';
import { formatDate, sanitizeText } from '../../shared/lib';
import { Icon, Skeleton, StatusBadge } from '../../shared/ui';

const tipos = ['', 'CONGRESO', 'SIMPOSIO', 'SEMINARIO', 'TALLER'];
const modalidades = ['', 'PRESENCIAL', 'VIRTUAL', 'HIBRIDO'];
const estados = ['', 'BORRADOR', 'PENDIENTE_PUBLICACION', 'PUBLICADO', 'RECHAZADO', 'CANCELADO', 'FINALIZADO'];
const estadoLabels: Record<string, string> = {
  BORRADOR: 'Borradores',
  PENDIENTE_PUBLICACION: 'Pendientes',
  PUBLICADO: 'Publicados',
  RECHAZADO: 'Rechazados',
  CANCELADO: 'Cancelados',
  FINALIZADO: 'Finalizados',
};

export function CatalogPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { roles, user } = useAuth();
  const role = getRolePresentation(roles);
  const canCreate = canCreateEvents(roles);
  const isParticipantCatalog = roles.includes('PARTICIPANTE') && !canManageEvents(roles);
  const isOrganizador = roles.includes('ORGANIZADOR') && !roles.includes('ADMIN');
  const isAdmin = roles.includes('ADMIN');
  const accessMessage = (location.state as { accessMessage?: string } | null)?.accessMessage;
  const [events, setEvents] = useState<AcademicEvent[]>([]);
  const [myInscriptions, setMyInscriptions] = useState<Inscription[]>([]);
  // Participantes ven solo eventos con cupos; gestores ven todos sus estados
  const [filters, setFilters] = useState<EventFilters>(() => ({
    conCupos: isParticipantCatalog ? true : undefined,
  }));
  const [onlyConfirmed, setOnlyConfirmed] = useState(false);
  const [search, setSearch] = useState('');
  const [isLoading, setLoading] = useState(true);
  const [error, setError] = useState<AppError | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  const activeFilters = useMemo<EventFilters>(
    () => ({
      ...filters,
      buscar: search.trim() || undefined,
      // ORGANIZADOR y ADMIN deben ver sus propios eventos en cualquier estado (BORRADOR, etc.)
      incluirPropios: isOrganizador || isAdmin ? true : undefined,
    }),
    [filters, search, isOrganizador, isAdmin],
  );
  const confirmedByEventId = useMemo(() => new Map(
    myInscriptions
      .filter((inscription) => ['CONFIRMADA', 'ASISTENCIA_REGISTRADA', 'CERTIFICADO_EMITIDO'].includes(inscription.estado))
      .map((inscription) => [inscription.eventoId, inscription]),
  ), [myInscriptions]);
  const visibleEvents = useMemo(
    () => onlyConfirmed
      ? events.filter((event) => confirmedByEventId.has(event.id))
      : events,
    [events, onlyConfirmed, confirmedByEventId],
  );

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    Promise.all([
      listEvents(activeFilters),
      isParticipantCatalog ? listMyInscriptions() : Promise.resolve([]),
    ])
      .then(([eventResponse, inscriptionResponse]) => {
        if (!mounted) return;
        setEvents(eventResponse);
        setMyInscriptions(inscriptionResponse);
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
  }, [activeFilters, retryVersion, isParticipantCatalog]);

  useEffect(() => {
    const refresh = () => setRetryVersion((current) => current + 1);
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => {
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, []);

  return (
    <section className="page-grid">
      <div className="page-title">
        <div>
          <span className="eyebrow">{canManageEvents(roles) ? 'Gestión de eventos' : 'Catálogo'}</span>
          <h1>{canManageEvents(roles) ? 'Mis eventos' : 'Eventos disponibles'}</h1>
        </div>
        <div className="service-strip" aria-label="Servicios conectados">
          <span>event-service</span>
          <span>Redis cache</span>
          <span>JWT activo</span>
        </div>
        {canCreate && (
          <button className="button button--primary" type="button" onClick={() => navigate('/eventos/nuevo')}>
            <Icon name="plus" />
            Crear evento
          </button>
        )}
      </div>

      <div className={`role-context-banner role-context-banner--${role.tone}`} role="status">
        <strong>{role.label}</strong>
        <span>{role.catalogMessage}</span>
      </div>

      {accessMessage && (
        <div className="alert alert--warning" role="alert">
          {accessMessage}
        </div>
      )}

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
        {canManageEvents(roles) && (
          <select
            className="select"
            aria-label="Filtrar por estado"
            value={filters.estado ?? ''}
            onChange={(event) => setFilters((current) => ({ ...current, estado: event.target.value as EventFilters['estado'] || undefined }))}
          >
            {estados.map((estado) => (
              <option key={estado || 'todos-estados'} value={estado}>
                {estado ? estadoLabels[estado] : 'Todos los estados'}
              </option>
            ))}
          </select>
        )}
        {isParticipantCatalog && (
          <label className="filter-toggle">
            <input
              type="checkbox"
              checked={onlyConfirmed}
              onChange={(event) => setOnlyConfirmed(event.target.checked)}
            />
            <span>Mis confirmados</span>
          </label>
        )}
      </div>

      {error && <ContextualError error={error} onRetry={() => setRetryVersion((current) => current + 1)} />}
      {isLoading && <Skeleton rows={6} />}

      {!isLoading && !error && (
        <div className="event-grid">
          {visibleEvents.map((event) => {
            const canEdit = canManageEvent(roles, user?.id, event.organizadorId);
            const confirmedInscription = confirmedByEventId.get(event.id);

            return (
              <article className={`event-card event-card--state-${event.estado.toLowerCase()}${confirmedInscription ? ' event-card--confirmed' : ''}`} key={event.id}>
                <div className="event-card__top">
                  <span className="event-card__type">{event.tipo}</span>
                  <StatusBadge value={event.estado} />
                </div>
                {confirmedInscription && (
                  <div className="event-card__badges" aria-label="Estado de tu inscripción">
                    <span className="management-badge management-badge--confirmed">Inscrito</span>
                  </div>
                )}
                {canEdit && (
                  <div className="event-card__badges" aria-label="Permisos sobre el evento">
                    <span className="management-badge">Editable</span>
                    {isAdmin && <span className="management-badge management-badge--admin">Admin</span>}
                  </div>
                )}
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
                <div className="event-card__actions">
                  <button className="button button--secondary button--wide" type="button" onClick={() => navigate(`/eventos/${event.id}`)}>
                    Ver detalle
                  </button>
                  {canEdit && (
                    <button className="button button--ghost button--wide" type="button" onClick={() => navigate(`/eventos/${event.id}/editar`)}>
                      <Icon name="edit" />
                      Editar
                    </button>
                  )}
                  {confirmedInscription && (
                    <button className="button button--ghost button--wide" type="button" onClick={() => navigate(`/confirmacion/${confirmedInscription.inscripcionId}`)}>
                      <Icon name="check" />
                      Ver confirmación
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {!isLoading && !error && visibleEvents.length === 0 && (
        <div className="empty-state">
          {canManageEvents(roles)
            ? 'No tienes eventos creados. Usa "Crear evento" para comenzar.'
            : 'No hay eventos publicados para los filtros seleccionados.'}
        </div>
      )}
    </section>
  );
}
