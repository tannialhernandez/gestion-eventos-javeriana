package com.javeriana.eventos.payment.infrastructure.persistence.entity;

import com.javeriana.eventos.payment.domain.model.EstadoPago;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pago")
public class PagoEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "inscripcion_id", nullable = false, columnDefinition = "uuid")
    private UUID inscripcionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 30)
    private String pasarela;

    @Column(name = "referencia_externa", unique = true, length = 200)
    private String referenciaExterna;

    @Column(name = "preferencia_externa_id", length = 200)
    private String preferenciaExternaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_confirmacion")
    private Instant fechaConfirmacion;

    @Column(name = "fecha_reembolso")
    private Instant fechaReembolso;

    @Column(name = "intentos_cobro", nullable = false)
    private int intentosCobro;

    @Column(name = "metadatos_pasarela", columnDefinition = "text")
    private String metadatosPasarela;

    @Version
    private int version;

    // Getters y setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInscripcionId() { return inscripcionId; }
    public void setInscripcionId(UUID inscripcionId) { this.inscripcionId = inscripcionId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public String getPasarela() { return pasarela; }
    public void setPasarela(String pasarela) { this.pasarela = pasarela; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }
    public String getPreferenciaExternaId() { return preferenciaExternaId; }
    public void setPreferenciaExternaId(String preferenciaExternaId) { this.preferenciaExternaId = preferenciaExternaId; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaConfirmacion() { return fechaConfirmacion; }
    public void setFechaConfirmacion(Instant fechaConfirmacion) { this.fechaConfirmacion = fechaConfirmacion; }
    public Instant getFechaReembolso() { return fechaReembolso; }
    public void setFechaReembolso(Instant fechaReembolso) { this.fechaReembolso = fechaReembolso; }
    public int getIntentosCobro() { return intentosCobro; }
    public void setIntentosCobro(int intentosCobro) { this.intentosCobro = intentosCobro; }
    public String getMetadatosPasarela() { return metadatosPasarela; }
    public void setMetadatosPasarela(String metadatosPasarela) { this.metadatosPasarela = metadatosPasarela; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
