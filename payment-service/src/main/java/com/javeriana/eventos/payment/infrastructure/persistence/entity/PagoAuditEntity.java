package com.javeriana.eventos.payment.infrastructure.persistence.entity;

import com.javeriana.eventos.payment.domain.audit.EventoAuditoria;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pago_audit")
public class PagoAuditEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "pago_id", nullable = false, columnDefinition = "uuid")
    private UUID pagoId;

    @Column(name = "inscripcion_id", nullable = false, columnDefinition = "uuid")
    private UUID inscripcionId;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(length = 500)
    private String motivo;

    @Column(columnDefinition = "text")
    private String metadatos;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    protected PagoAuditEntity() {}

    public static PagoAuditEntity from(EventoAuditoria evento) {
        PagoAuditEntity e = new PagoAuditEntity();
        e.id            = UUID.randomUUID();
        e.pagoId        = evento.pagoId();
        e.inscripcionId = evento.inscripcionId();
        e.estadoAnterior = evento.estadoAnterior();
        e.estadoNuevo   = evento.estadoNuevo();
        e.actor         = evento.actor();
        e.motivo        = evento.motivo();
        e.metadatos     = evento.metadatos();
        e.ocurridoEn    = evento.ocurridoEn();
        e.ipOrigen      = evento.ipOrigen();
        return e;
    }

    public EventoAuditoria toDomain() {
        return new EventoAuditoria(pagoId, inscripcionId, estadoAnterior, estadoNuevo,
            actor, motivo, metadatos, ocurridoEn, ipOrigen);
    }

    public UUID getId()             { return id; }
    public UUID getPagoId()         { return pagoId; }
    public UUID getInscripcionId()  { return inscripcionId; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public String getEstadoNuevo()  { return estadoNuevo; }
    public String getActor()        { return actor; }
    public String getMotivo()       { return motivo; }
    public String getMetadatos()    { return metadatos; }
    public Instant getOcurridoEn()  { return ocurridoEn; }
    public String getIpOrigen()     { return ipOrigen; }
}
