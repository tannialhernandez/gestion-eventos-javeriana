import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ContextualError } from '../../components';
import type { AcademicEvent, Tariff } from '../../entities/event';
import type { AttendanceRecord, Inscription } from '../../entities/inscription';
import { useAuth } from '../auth';
import { canManageEvent, canManageEvents } from '../auth/rolePresentation';
import {
  cancelInscription,
  createInscription,
  downloadCertificate,
  getMyAttendance,
  getMyInscriptionForEvent,
  listEventAttendance,
  markAttendance,
} from '../../services/inscriptionService';
import { approveEvent, cancelEvent, getEvent, listTariffs, rejectEvent, sendEventToReview } from '../../services/eventService';
import { BusinessRuleError, type AppError, normalizeAppError } from '../../lib/errors';
import { formatDate, formatDateTime, formatMoney, sanitizeText } from '../../shared/lib';
import { Icon, Skeleton, StatusBadge } from '../../shared/ui';
import { saveCheckoutSnapshot } from '../checkout/storage';

export function EventDetailPage() {
  const { eventoId } = useParams<{ eventoId: string }>();
  const navigate = useNavigate();
  const { roles, user } = useAuth();
  const [event, setEvent] = useState<AcademicEvent | null>(null);
  const [tariffs, setTariffs] = useState<Tariff[]>([]);
  const [currentInscription, setCurrentInscription] = useState<Inscription | null>(null);
  const [attendanceRecords, setAttendanceRecords] = useState<AttendanceRecord[]>([]);
  const [currentAttendance, setCurrentAttendance] = useState<AttendanceRecord | null>(null);
  const [selectedTariffId, setSelectedTariffId] = useState<string>('');
  const [isLoading, setLoading] = useState(true);
  const [isSubmitting, setSubmitting] = useState(false);
  const [attendanceUpdatingId, setAttendanceUpdatingId] = useState<string | null>(null);
  const [error, setError] = useState<AppError | null>(null);
  const [attendanceError, setAttendanceError] = useState<AppError | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);
  const [attendanceVersion, setAttendanceVersion] = useState(0);
  const isManagerRole = canManageEvents(roles);
  const isParticipantRole = roles.includes('PARTICIPANTE') && !isManagerRole;

  useEffect(() => {
    if (!eventoId) return;
    let mounted = true;
    setLoading(true);
    setError(null);

    Promise.all([
      getEvent(eventoId),
      listTariffs(eventoId),
      isParticipantRole ? getMyInscriptionForEvent(eventoId) : Promise.resolve(null),
    ])
      .then(([eventResponse, tariffResponse, inscriptionResponse]) => {
        if (!mounted) return;
        setEvent(eventResponse);
        setTariffs(tariffResponse);
        setCurrentInscription(inscriptionResponse);
        setSelectedTariffId(inscriptionResponse?.tarifaId ?? tariffResponse[0]?.id ?? '');
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
  }, [eventoId, retryVersion, isParticipantRole]);

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

  const selectedTariff = useMemo(() => tariffs.find((tariff) => tariff.id === selectedTariffId) ?? null, [tariffs, selectedTariffId]);
  const usedSeats = event ? event.cupoMaximo - event.cupoDisponible : 0;
  const seatsPercent = event ? Math.min(100, Math.round((usedSeats / event.cupoMaximo) * 100)) : 0;
  const isAdmin = roles.includes('ADMIN');
  const canEditEvent = event ? canManageEvent(roles, user?.id, event.organizadorId) : false;
  const isOrganizerOwner = Boolean(roles.includes('ORGANIZADOR') && user?.id === event?.organizadorId);
  const isTerminalEvent = event ? ['CANCELADO', 'FINALIZADO'].includes(event.estado) : false;
  const hasConfirmedInscription = ['CONFIRMADA', 'ASISTENCIA_REGISTRADA', 'CERTIFICADO_EMITIDO'].includes(currentInscription?.estado ?? '');
  const hasPendingPayment = currentInscription?.estado === 'PENDIENTE_PAGO';

  useEffect(() => {
    if (!event || !canEditEvent) {
      setAttendanceRecords([]);
      return;
    }

    let mounted = true;
    setAttendanceError(null);
    listEventAttendance(event.id)
      .then((records) => {
        if (mounted) setAttendanceRecords(records);
      })
      .catch((err) => {
        if (mounted) setAttendanceError(normalizeAppError(err));
      });

    return () => {
      mounted = false;
    };
  }, [event, canEditEvent, attendanceVersion]);

  useEffect(() => {
    if (!event || !isParticipantRole || !hasConfirmedInscription) {
      setCurrentAttendance(null);
      return;
    }

    let mounted = true;
    getMyAttendance(event.id)
      .then((attendance) => {
        if (mounted) setCurrentAttendance(attendance);
      })
      .catch((err) => {
        if (mounted) setAttendanceError(normalizeAppError(err));
      });

    return () => {
      mounted = false;
    };
  }, [event, isParticipantRole, hasConfirmedInscription, currentInscription?.inscripcionId, attendanceVersion]);

  const handleCreateInscription = async () => {
    if (!event || !selectedTariff) return;
    setSubmitting(true);
    setError(null);
    try {
      const inscription = await createInscription(event.id, selectedTariff.id);
      setCurrentInscription(inscription);
      saveCheckoutSnapshot(inscription.inscripcionId, {
        eventTitle: event.titulo,
        eventId: event.id,
        tariffId: selectedTariff.id,
        amount: selectedTariff.monto,
        currency: selectedTariff.moneda,
        checkoutUrl: inscription.checkoutUrl,
        expiresAt: inscription.fechaExpiracionPago,
      });
      if (inscription.estado === 'CONFIRMADA') {
        navigate(`/confirmacion/${inscription.inscripcionId}`);
      } else {
        navigate(`/inscripciones/${inscription.inscripcionId}/pago`);
      }
    } catch (err) {
      const apiError = normalizeAppError(err);
      setError(
        apiError.status === 409
          ? new BusinessRuleError('El evento ya no tiene cupos disponibles.', { status: 409, code: 'sin_cupos_disponibles' })
          : apiError,
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelEvent = async () => {
    if (!event || !window.confirm('¿Cancelar este evento?')) return;
    setSubmitting(true);
    setError(null);
    try {
      await cancelEvent(event.id, 'Cancelado desde detalle de gestión');
      const updated = await getEvent(event.id);
      setEvent(updated);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleEnviarRevision = async () => {
    if (!event) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await sendEventToReview(event.id);
      setEvent(updated);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleAprobar = async () => {
    if (!event || !window.confirm('¿Aprobar la publicación de este evento?')) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await approveEvent(event.id);
      setEvent(updated);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRechazar = async () => {
    if (!event) return;
    const motivo = window.prompt('Motivo del rechazo (obligatorio):');
    if (!motivo?.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await rejectEvent(event.id, motivo.trim());
      setEvent(updated);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelInscription = async () => {
    if (!currentInscription || !window.confirm('¿Darte de baja de este evento?')) return;
    setSubmitting(true);
    setError(null);
    try {
      const canceled = await cancelInscription(currentInscription.inscripcionId);
      setCurrentInscription(canceled);
      setRetryVersion((current) => current + 1);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleMarkAttendance = async (record: AttendanceRecord, asistio: boolean) => {
    if (!event) return;
    setAttendanceUpdatingId(record.inscripcionId);
    setAttendanceError(null);
    try {
      const updated = await markAttendance(event.id, record.inscripcionId, asistio);
      setAttendanceRecords((records) => records.map((item) => (
        item.inscripcionId === updated.inscripcionId ? updated : item
      )));
    } catch (err) {
      setAttendanceError(normalizeAppError(err));
    } finally {
      setAttendanceUpdatingId(null);
    }
  };

  const handleDownloadCertificate = async () => {
    if (!currentInscription) return;
    setSubmitting(true);
    setError(null);
    try {
      const blob = await downloadCertificate(currentInscription.inscripcionId);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `certificado-${currentInscription.inscripcionId}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(normalizeAppError(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading) return <Skeleton rows={2} />;

  if (error && !event) {
    return <ContextualError error={error} onRetry={() => setRetryVersion((current) => current + 1)} />;
  }

  if (!event) {
    return <div className="empty-state">El evento no está disponible.</div>;
  }

  return (
    <section className="detail-layout">
      <div className="detail-main">
        <button className="button button--ghost" type="button" onClick={() => navigate('/catalogo')}>
          <Icon name="arrow-left" />
          Volver
        </button>
        <div className="detail-heading">
          <div>
            <span className="eyebrow">{event.tipo}</span>
            <h1>{sanitizeText(event.titulo)}</h1>
          </div>
          <div className="detail-heading__actions">
            <StatusBadge value={event.estado} />
            {isAdmin && <span className="management-badge management-badge--admin">Admin</span>}
          </div>
        </div>
        <p className="detail-description">{sanitizeText(event.descripcion)}</p>

        <dl className="detail-facts">
          <div>
            <dt>Fecha de inicio</dt>
            <dd>{formatDate(event.fechaInicio)}</dd>
          </div>
          <div>
            <dt>Fecha de cierre</dt>
            <dd>{formatDate(event.fechaFin)}</dd>
          </div>
          <div>
            <dt>Límite de inscripción</dt>
            <dd>{formatDateTime(event.fechaLimiteInscripcion)}</dd>
          </div>
          <div>
            <dt>Modalidad</dt>
            <dd>{event.modalidad}</dd>
          </div>
        </dl>

        <div className="capacity-band">
          <div>
            <strong>{event.cupoDisponible} cupos disponibles</strong>
            <span>{usedSeats} de {event.cupoMaximo} reservados</span>
          </div>
          <div className="capacity-meter" aria-hidden="true">
            <span style={{ width: `${seatsPercent}%` }} />
          </div>
        </div>

        {canEditEvent && (
          <section className="attendance-section" aria-labelledby="attendance-title">
            <div className="attendance-section__header">
              <div>
                <span className="eyebrow">Asistencia</span>
                <h2 id="attendance-title">Registro de asistencia</h2>
              </div>
              <span>{attendanceRecords.length} inscripciones confirmadas</span>
            </div>

            {attendanceError && (
              <ContextualError error={attendanceError} onRetry={() => setAttendanceVersion((value) => value + 1)} />
            )}

            {!attendanceError && attendanceRecords.length === 0 ? (
              <p className="panel-copy">Aún no hay inscripciones confirmadas para marcar asistencia.</p>
            ) : (
              <div className="attendance-table-wrap">
                <table className="attendance-table">
                  <thead>
                    <tr>
                      <th>Participante</th>
                      <th>Inscripción</th>
                      <th>Estado</th>
                      <th>Asistió</th>
                    </tr>
                  </thead>
                  <tbody>
                    {attendanceRecords.map((record) => (
                      <tr key={record.inscripcionId}>
                        <td>{sanitizeText(record.participante)}</td>
                        <td><code>{record.inscripcionId.slice(0, 8)}</code></td>
                        <td><StatusBadge value={record.estado} /></td>
                        <td>
                          <label className="attendance-check">
                            <input
                              type="checkbox"
                              checked={record.asistio}
                              disabled={attendanceUpdatingId === record.inscripcionId}
                              onChange={(event) => handleMarkAttendance(record, event.target.checked)}
                              aria-label={`Marcar asistencia de ${record.participante}`}
                            />
                            <span>{record.asistio ? 'Confirmada' : 'Pendiente'}</span>
                          </label>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </div>

      <section className="checkout-panel" aria-label={isManagerRole ? 'Gestión del evento' : 'Inscripción'}>
        {/* Guard: participante que navega directo a URL de borrador */}
        {isParticipantRole && event.estado !== 'PUBLICADO' ? (
          <div className="alert alert--warning">Este evento no está disponible para inscripciones.</div>
        ) : isManagerRole ? (
          <>
            <h2>Gestión</h2>
            {canEditEvent ? (
              <>
                {/* ── Acciones de workflow según estado ────────────── */}
                {event.estado === 'BORRADOR' && isOrganizerOwner && (
                  <p className="panel-copy">Borrador. Envíalo a aprobación para que un administrador lo publique.</p>
                )}
                {event.estado === 'BORRADOR' && !isOrganizerOwner && (
                  <p className="panel-copy">Borrador del organizador. El administrador puede editarlo o cancelarlo, pero la solicitud de publicación la inicia el organizador.</p>
                )}
                {event.estado === 'BORRADOR' && isOrganizerOwner && (
                  <button className="button button--primary button--wide" type="button" onClick={handleEnviarRevision} disabled={isSubmitting}>
                    <Icon name="send" />
                    {isSubmitting ? 'Enviando' : 'Enviar a aprobación'}
                  </button>
                )}
                {event.estado === 'PENDIENTE_PUBLICACION' && !isAdmin && (
                  <p className="panel-copy">En revisión — un administrador aprobará o rechazará este evento pronto.</p>
                )}
                {event.estado === 'PENDIENTE_PUBLICACION' && isAdmin && (
                  <>
                    <p className="panel-copy">Pendiente de aprobación. Revisa el evento y decide.</p>
                    <button className="button button--primary button--wide" type="button" onClick={handleAprobar} disabled={isSubmitting}>
                      <Icon name="check" />
                      {isSubmitting ? 'Aprobando…' : 'Aprobar publicación'}
                    </button>
                    <button className="button button--danger button--wide" type="button" onClick={handleRechazar} disabled={isSubmitting}>
                      <Icon name="x" />
                      Rechazar
                    </button>
                  </>
                )}
                {event.estado === 'RECHAZADO' && isOrganizerOwner && (
                  <>
                    <div className="alert alert--error">Evento rechazado. Corrígelo y re-envíalo a revisión.</div>
                    <button className="button button--primary button--wide" type="button" onClick={handleEnviarRevision} disabled={isSubmitting}>
                      <Icon name="send" />
                      {isSubmitting ? 'Enviando' : 'Re-enviar a aprobación'}
                    </button>
                  </>
                )}
                {event.estado === 'RECHAZADO' && !isOrganizerOwner && (
                  <div className="alert alert--error">Evento rechazado. El organizador debe corregirlo y enviarlo nuevamente a aprobación.</div>
                )}
                {event.estado === 'PUBLICADO' && (
                  <p className="panel-copy">Publicado. Para retirarlo del catálogo público, edítalo y selecciona guardar como borrador.</p>
                )}
                {event.estado === 'CANCELADO' && (
                  <div className="alert alert--error">Evento cancelado. Este estado es terminal y no acepta nuevas acciones.</div>
                )}

                {/* ── Acciones base (siempre disponibles para canEditEvent) ── */}
                <button className="button button--secondary button--wide" type="button" onClick={() => navigate(`/eventos/${event.id}/editar`)} disabled={isTerminalEvent}>
                  <Icon name="edit" />
                  Editar
                </button>
                <button className="button button--danger button--wide" type="button" onClick={handleCancelEvent} disabled={isSubmitting || isTerminalEvent}>
                  <Icon name="trash" />
                  Cancelar evento
                </button>

                {error && <ContextualError error={error} onRetry={() => setRetryVersion((v) => v + 1)} />}
              </>
            ) : (
              <div className="alert alert--warning">Este evento pertenece a otro organizador. Solo ADMIN puede gestionarlo.</div>
            )}
          </>
        ) : isParticipantRole && hasConfirmedInscription ? (
          <>
            <h2>Inscripción</h2>
            <StatusBadge value="CONFIRMADA" />
            <p className="panel-copy">Ya estás inscrito en este evento. Tu cupo está reservado.</p>
            <button className="button button--secondary button--wide" type="button" onClick={() => navigate(`/confirmacion/${currentInscription?.inscripcionId}`)}>
              <Icon name="check" />
              Ver confirmación
            </button>
            {currentAttendance?.asistio ? (
              <button className="button button--primary button--wide" type="button" onClick={handleDownloadCertificate} disabled={isSubmitting}>
                <Icon name="download" />
                {isSubmitting ? 'Generando certificado' : 'Descargar certificado'}
              </button>
            ) : (
              <div className="alert alert--info">Certificado disponible cuando se confirme asistencia.</div>
            )}
            <button className="button button--danger button--wide" type="button" onClick={handleCancelInscription} disabled={isSubmitting}>
              <Icon name="trash" />
              {isSubmitting ? 'Cancelando inscripción' : 'Darme de baja'}
            </button>
            {error && <ContextualError error={error} onRetry={handleCancelInscription} />}
          </>
        ) : isParticipantRole && hasPendingPayment && selectedTariff ? (
          <>
            <h2>Inscripción</h2>
            <StatusBadge value="PENDIENTE_PAGO" />
            <p className="panel-copy">Tienes una inscripción pendiente. Completa el pago para confirmar tu cupo.</p>
            <div className="price-line">
              <span>Total pendiente</span>
              <strong>{formatMoney(selectedTariff.monto, selectedTariff.moneda)}</strong>
            </div>
            {error && <ContextualError error={error} onRetry={handleCreateInscription} />}
            <button
              className="button button--primary button--wide"
              type="button"
              onClick={handleCreateInscription}
              disabled={isSubmitting}
            >
              <Icon name="credit-card" />
              {isSubmitting ? 'Preparando pago' : 'Continuar pago'}
            </button>
            <button className="button button--danger button--wide" type="button" onClick={handleCancelInscription} disabled={isSubmitting}>
              <Icon name="trash" />
              {isSubmitting ? 'Cancelando inscripción' : 'Darme de baja'}
            </button>
          </>
        ) : isParticipantRole && tariffs.length > 0 ? (
          <>
            <h2>Inscripción</h2>
            <label className="field">
              <span>Tarifa</span>
              <select className="select" value={selectedTariffId} onChange={(event) => setSelectedTariffId(event.target.value)}>
                {tariffs.map((tariff) => (
                  <option key={tariff.id} value={tariff.id}>
                    {tariff.descripcion} - {formatMoney(tariff.monto, tariff.moneda)}
                  </option>
                ))}
              </select>
            </label>
            <div className="price-line">
              <span>Total</span>
              <strong>{selectedTariff ? formatMoney(selectedTariff.monto, selectedTariff.moneda) : '-'}</strong>
            </div>
            {error && <ContextualError error={error} onRetry={handleCreateInscription} />}
            <button
              className="button button--primary button--wide"
              type="button"
              onClick={handleCreateInscription}
              disabled={!event.aceptaInscripciones || event.cupoDisponible <= 0 || isSubmitting}
            >
              <Icon name="credit-card" />
              {isSubmitting ? 'Reservando cupo' : 'Inscribirme'}
            </button>
          </>
        ) : isParticipantRole ? (
          <>
            <h2>Inscripción</h2>
            <div className="alert alert--warning">No hay tarifas activas para este evento.</div>
          </>
        ) : (
          <>
            <h2>Evento</h2>
            <div className="alert alert--warning">Tu rol actual no habilita acciones sobre este evento.</div>
          </>
        )}
      </section>
    </section>
  );
}
