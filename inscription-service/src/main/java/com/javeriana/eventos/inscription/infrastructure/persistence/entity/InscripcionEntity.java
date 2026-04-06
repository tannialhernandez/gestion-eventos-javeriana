package com.javeriana.eventos.inscription.infrastructure.persistence.entity;

import com.javeriana.eventos.inscription.domain.model.EstadoInscripcion;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inscripcion",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_inscripcion_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_usuario_evento", columnNames = {"usuario_id", "evento_id"})
    }
)
public class InscripcionEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "usuario_id", nullable = false, columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "evento_id", nullable = false, columnDefinition = "uuid")
    private UUID eventoId;

    @Column(name = "tarifa_id", nullable = false, columnDefinition = "uuid")
    private UUID tarifaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoInscripcion estado;

    @Column(name = "fecha_inscripcion", nullable = false)
    private Instant fechaInscripcion;

    @Column(name = "fecha_expiracion_pago")
    private Instant fechaExpiracionPago;

    @Column(name = "codigo_qr", length = 500)
    private String codigoQr;

    @Column(name = "idempotency_key", nullable = false, columnDefinition = "uuid")
    private UUID idempotencyKey;

    @Version
    private int version;

    // Getters y setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public UUID getEventoId() { return eventoId; }
    public void setEventoId(UUID eventoId) { this.eventoId = eventoId; }
    public UUID getTarifaId() { return tarifaId; }
    public void setTarifaId(UUID tarifaId) { this.tarifaId = tarifaId; }
    public EstadoInscripcion getEstado() { return estado; }
    public void setEstado(EstadoInscripcion estado) { this.estado = estado; }
    public Instant getFechaInscripcion() { return fechaInscripcion; }
    public void setFechaInscripcion(Instant fechaInscripcion) { this.fechaInscripcion = fechaInscripcion; }
    public Instant getFechaExpiracionPago() { return fechaExpiracionPago; }
    public void setFechaExpiracionPago(Instant fechaExpiracionPago) { this.fechaExpiracionPago = fechaExpiracionPago; }
    public String getCodigoQr() { return codigoQr; }
    public void setCodigoQr(String codigoQr) { this.codigoQr = codigoQr; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
