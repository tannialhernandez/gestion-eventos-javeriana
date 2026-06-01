package com.javeriana.eventos.payment.domain.model;

import com.javeriana.eventos.shared.domain.AggregateRoot;
import com.javeriana.eventos.shared.domain.BusinessRuleViolationException;
import com.javeriana.eventos.payment.domain.events.PagoConfirmadoEvent;
import com.javeriana.eventos.payment.domain.events.PagoFallidoEvent;
import com.javeriana.eventos.payment.domain.events.PagoReembolsadoEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado raíz: PAGO
 *
 * Invariante central: referencia_externa es UNIQUE.
 * Antes de confirmar, se verifica que no exista otro pago con la misma
 * referencia_externa — esto garantiza idempotencia ante webhooks duplicados
 * de la pasarela (docs/comportamiento-runtime-inscripcion-pago.md §5).
 */
public class Pago extends AggregateRoot {

    private UUID id;
    private UUID inscripcionId;
    private BigDecimal monto;
    private String moneda;
    private String pasarela;              // MERCADOPAGO, SIMULADOR
    private String referenciaExterna;    // ID en la pasarela — UNIQUE (ADR-09)
    private String preferenciaExternaId; // preference_id de MercadoPago
    private EstadoPago estado;
    private Instant fechaCreacion;
    private Instant fechaConfirmacion;
    private Instant fechaReembolso;
    private int intentosCobro;
    private String metadatosPasarela;    // JSON del webhook (auditoría)
    private int version;

    // Constructor para nuevo pago
    public Pago(UUID id, UUID inscripcionId, BigDecimal monto, String moneda, String pasarela) {
        this.id = id;
        this.inscripcionId = inscripcionId;
        this.monto = monto;
        this.moneda = moneda;
        this.pasarela = pasarela;
        this.estado = EstadoPago.INICIADO;
        this.fechaCreacion = Instant.now();
        this.intentosCobro = 0;
        this.version = 0;
    }

    // Constructor de reconstrucción desde persistencia
    public Pago(UUID id, UUID inscripcionId, BigDecimal monto, String moneda,
                String pasarela, String referenciaExterna, String preferenciaExternaId,
                EstadoPago estado, Instant fechaCreacion, Instant fechaConfirmacion,
                Instant fechaReembolso, int intentosCobro, String metadatosPasarela, int version) {
        this.id = id;
        this.inscripcionId = inscripcionId;
        this.monto = monto;
        this.moneda = moneda;
        this.pasarela = pasarela;
        this.referenciaExterna = referenciaExterna;
        this.preferenciaExternaId = preferenciaExternaId;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaConfirmacion = fechaConfirmacion;
        this.fechaReembolso = fechaReembolso;
        this.intentosCobro = intentosCobro;
        this.metadatosPasarela = metadatosPasarela;
        this.version = version;
    }

    // ─── Comportamiento de dominio ─────────────────────────────────────────────

    /**
     * Registra la preferencia creada en la pasarela (checkout_url obtenido).
     */
    public void registrarPreferencia(String preferenciaExternaId) {
        this.preferenciaExternaId = preferenciaExternaId;
        this.estado = EstadoPago.PROCESANDO;
    }

    /**
     * RN-03: Confirma el pago. Solo puede confirmarse una vez (idempotencia).
     * La verificación de referenciaExterna UNIQUE ocurre en el repositorio
     * antes de llegar aquí.
     */
    public void confirmar(String referenciaExterna, String metadatosPasarela) {
        if (this.estado == EstadoPago.CONFIRMADO) {
            throw new BusinessRuleViolationException(
                "RN-PAGO-01",
                "El pago ya está confirmado. Webhook duplicado para referencia: " + referenciaExterna
            );
        }
        if (this.estado == EstadoPago.REEMBOLSADO || this.estado == EstadoPago.FALLIDO) {
            throw new BusinessRuleViolationException(
                "RN-PAGO-02",
                "No se puede confirmar un pago en estado: " + this.estado
            );
        }
        this.estado = EstadoPago.CONFIRMADO;
        this.referenciaExterna = referenciaExterna;
        this.metadatosPasarela = metadatosPasarela;
        this.fechaConfirmacion = Instant.now();
        this.intentosCobro++;

        registerEvent(new PagoConfirmadoEvent(this.id, this.inscripcionId, referenciaExterna,
            this.monto, this.moneda, this.fechaConfirmacion));
    }

    /**
     * Marca el pago como fallido. Si el número de intentos supera el límite,
     * inscription-service libera el cupo.
     */
    public void marcarFallido(String motivoRechazo) {
        if (this.estado.esFinal()) {
            throw new BusinessRuleViolationException(
                "RN-PAGO-03",
                "No se puede marcar como fallido un pago en estado: " + this.estado
            );
        }
        this.estado = EstadoPago.FALLIDO;
        this.intentosCobro++;

        registerEvent(new PagoFallidoEvent(this.id, this.inscripcionId,
            motivoRechazo, "Pago rechazado por la pasarela de pago",
            this.monto, this.moneda));
    }

    /**
     * Emite reembolso (cancelación posterior a un pago ya CONFIRMADO).
     * Para pago tardío post-expiración usar reembolsarPorExpiracion().
     */
    public void reembolsar() {
        if (this.estado != EstadoPago.CONFIRMADO) {
            throw new BusinessRuleViolationException(
                "RN-PAGO-04",
                "Solo se puede reembolsar un pago CONFIRMADO. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoPago.REEMBOLSADO;
        this.fechaReembolso = Instant.now();

        registerEvent(new PagoReembolsadoEvent(this.id, this.inscripcionId,
            this.monto, this.moneda, this.fechaReembolso));
    }

    /**
     * Reembolsa un pago aprobado por la pasarela cuya inscripción ya expiró.
     *
     * RN-10 (SRS §9.3.3, docs/srs-seccion9-modelo-datos-seccion10-trazabilidad.md):
     * El deadline de pago es fecha_inscripcion + 15 min. Si el webhook llega
     * después, la inscripción fue expirada por inscription-service; el pago debe
     * reembolsarse sin pasar por CONFIRMADO.
     *
     * RN-PAGO-05 (propuesta — docs/srs-casos-uso-pendientes.md §11):
     * Un pago en estado final no puede ser reembolsado por expiración.
     *
     * Diferencia con reembolsar(): válido desde INICIADO/PROCESANDO (no-finales).
     */
    public void reembolsarPorExpiracion() {
        if (this.estado.esFinal()) {
            throw new BusinessRuleViolationException(
                "RN-PAGO-05",
                "No se puede reembolsar por expiración un pago en estado final: " + this.estado
            );
        }
        this.estado = EstadoPago.REEMBOLSADO;
        this.fechaReembolso = Instant.now();

        registerEvent(new PagoReembolsadoEvent(this.id, this.inscripcionId,
            this.monto, this.moneda, this.fechaReembolso));
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getInscripcionId() { return inscripcionId; }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public String getPasarela() { return pasarela; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getPreferenciaExternaId() { return preferenciaExternaId; }
    public EstadoPago getEstado() { return estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaConfirmacion() { return fechaConfirmacion; }
    public Instant getFechaReembolso() { return fechaReembolso; }
    public int getIntentosCobro() { return intentosCobro; }
    public String getMetadatosPasarela() { return metadatosPasarela; }
    public int getVersion() { return version; }
}
