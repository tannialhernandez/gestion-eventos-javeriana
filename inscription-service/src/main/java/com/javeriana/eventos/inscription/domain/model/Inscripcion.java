package com.javeriana.eventos.inscription.domain.model;

import com.javeriana.eventos.shared.domain.AggregateRoot;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import com.javeriana.eventos.inscription.domain.events.InscripcionConfirmadaEvent;
import com.javeriana.eventos.inscription.domain.events.InscripcionCreadaEvent;
import com.javeriana.eventos.inscription.domain.events.InscripcionExpiradaEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Agregado raíz: INSCRIPCION
 *
 * Contiene las transiciones de estado con sus invariantes.
 * El bloqueo pesimista (SELECT FOR UPDATE) sobre el cupo del evento
 * ocurre en el repositorio; este agregado valida las reglas de negocio
 * una vez que el control ya fue obtenido.
 *
 * Máquina de estados (docs/modelo-datos-conceptual.md §4.2):
 *   PENDIENTE_PAGO → CONFIRMADA (pago exitoso vía webhook)
 *   PENDIENTE_PAGO → EXPIRADA  (timeout 15 min, cupo se libera)
 *   CONFIRMADA → ASISTENCIA_REGISTRADA → CERTIFICADO_EMITIDO
 */
public class Inscripcion extends AggregateRoot {

    private UUID id;
    private UUID usuarioId;
    private UUID eventoId;
    private UUID tarifaId;
    private EstadoInscripcion estado;
    private Instant fechaInscripcion;
    private Instant fechaExpiracionPago;   // NOW() + 15 minutos (RN-02)
    private String codigoQr;
    private UUID idempotencyKey;            // ADR-09: previene inscripciones duplicadas
    private int version;

    private static final int MINUTOS_EXPIRACION = 15;

    // Constructor para nueva inscripción
    public Inscripcion(UUID id, UUID usuarioId, UUID eventoId,
                       UUID tarifaId, UUID idempotencyKey) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.eventoId = eventoId;
        this.tarifaId = tarifaId;
        this.estado = EstadoInscripcion.PENDIENTE_PAGO;
        this.fechaInscripcion = Instant.now();
        this.fechaExpiracionPago = this.fechaInscripcion.plusSeconds(MINUTOS_EXPIRACION * 60L);
        this.idempotencyKey = idempotencyKey;
        this.version = 0;

        // Emitir evento INSCRIPCION_CREADA para que notification-service
        // pueda enviar recordatorio de pago (ADR-020, Prompt 15)
        registerEvent(new InscripcionCreadaEvent(
            this.id, this.usuarioId, this.eventoId,
            this.tarifaId, this.fechaExpiracionPago));
    }

    // Constructor de reconstrucción desde persistencia
    public Inscripcion(UUID id, UUID usuarioId, UUID eventoId, UUID tarifaId,
                       EstadoInscripcion estado, Instant fechaInscripcion,
                       Instant fechaExpiracionPago, String codigoQr,
                       UUID idempotencyKey, int version) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.eventoId = eventoId;
        this.tarifaId = tarifaId;
        this.estado = estado;
        this.fechaInscripcion = fechaInscripcion;
        this.fechaExpiracionPago = fechaExpiracionPago;
        this.codigoQr = codigoQr;
        this.idempotencyKey = idempotencyKey;
        this.version = version;
    }

    // ─── Comportamiento de dominio ─────────────────────────────────────────────

    /**
     * RN-02: Confirma la inscripción tras recibir el webhook de pago exitoso.
     * Solo puede confirmarse si está en PENDIENTE_PAGO.
     * El código QR se genera aquí y queda vinculado a esta inscripción.
     */
    public void confirmar(String codigoQr) {
        if (this.estado != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-01",
                "Solo una inscripción PENDIENTE_PAGO puede confirmarse. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoInscripcion.CONFIRMADA;
        this.codigoQr = codigoQr;
        this.fechaExpiracionPago = null; // Ya no aplica el timeout
        registerEvent(new InscripcionConfirmadaEvent(this.id, this.usuarioId, this.eventoId));
    }

    /**
     * RN-02: El timeout de 15 minutos ha pasado sin que el pago se completara.
     * El job scheduler (InscripcionExpirationJob) invoca este método.
     * El cupo se libera en el servicio de aplicación, que actualiza el evento.
     */
    public void expirar() {
        if (this.estado != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-02",
                "Solo una inscripción PENDIENTE_PAGO puede expirar. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoInscripcion.EXPIRADA;
        registerEvent(new InscripcionExpiradaEvent(this.id, this.usuarioId, this.eventoId));
    }

    public void renovarVentanaPago() {
        if (this.estado != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-04",
                "Solo una inscripción PENDIENTE_PAGO puede renovar pago. Estado actual: " + this.estado
            );
        }
        this.fechaInscripcion = Instant.now();
        this.fechaExpiracionPago = this.fechaInscripcion.plusSeconds(MINUTOS_EXPIRACION * 60L);
    }

    public void reabrirParaPago(UUID tarifaId) {
        if (this.estado != EstadoInscripcion.EXPIRADA) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-04",
                "Solo una inscripción EXPIRADA puede reabrirse para pago. Estado actual: " + this.estado
            );
        }
        this.tarifaId = tarifaId;
        this.estado = EstadoInscripcion.PENDIENTE_PAGO;
        this.codigoQr = null;
        renovarVentanaPago();
    }

    public void reabrirDesdeCancelacion(UUID tarifaId, UUID idempotencyKey) {
        if (this.estado != EstadoInscripcion.CANCELADA) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-04",
                "Solo una inscripción CANCELADA puede reabrirse para pago. Estado actual: " + this.estado
            );
        }
        this.tarifaId = tarifaId;
        this.idempotencyKey = idempotencyKey;
        this.estado = EstadoInscripcion.PENDIENTE_PAGO;
        this.codigoQr = null;
        this.fechaInscripcion = Instant.now();
        this.fechaExpiracionPago = this.fechaInscripcion.plusSeconds(MINUTOS_EXPIRACION * 60L);
        registerEvent(new InscripcionCreadaEvent(
            this.id, this.usuarioId, this.eventoId,
            this.tarifaId, this.fechaExpiracionPago));
    }

    public void cancelar() {
        if (this.estado == EstadoInscripcion.CANCELADA) {
            return;
        }
        if (this.estado != EstadoInscripcion.CONFIRMADA
            && this.estado != EstadoInscripcion.PENDIENTE_PAGO) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-05",
                "Solo una inscripción CONFIRMADA o PENDIENTE_PAGO puede cancelarse. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoInscripcion.CANCELADA;
        this.fechaExpiracionPago = null;
    }

    /**
     * Verifica si la inscripción ha superado el tiempo límite de pago.
     */
    public boolean haExpirado() {
        return this.estado == EstadoInscripcion.PENDIENTE_PAGO
            && Instant.now().isAfter(this.fechaExpiracionPago);
    }

    /**
     * Registra la asistencia del participante al evento (scan QR).
     */
    public void registrarAsistencia() {
        if (this.estado != EstadoInscripcion.CONFIRMADA) {
            throw new BusinessRuleViolationException(
                "RN-INSCRIPCION-03",
                "Solo una inscripción CONFIRMADA puede registrar asistencia. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoInscripcion.ASISTENCIA_REGISTRADA;
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UUID getEventoId() { return eventoId; }
    public UUID getTarifaId() { return tarifaId; }
    public EstadoInscripcion getEstado() { return estado; }
    public Instant getFechaInscripcion() { return fechaInscripcion; }
    public Instant getFechaExpiracionPago() { return fechaExpiracionPago; }
    public String getCodigoQr() { return codigoQr; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public int getVersion() { return version; }
}
