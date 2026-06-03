package com.javeriana.eventos.inscription.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asistencia",
    uniqueConstraints = @UniqueConstraint(name = "uk_asistencia_inscripcion", columnNames = "inscripcion_id"))
public class AsistenciaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "inscripcion_id", nullable = false, columnDefinition = "uuid")
    private UUID inscripcionId;

    @Column(nullable = false)
    private boolean asistio;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "registrado_por", nullable = false, length = 255)
    private String registradoPor;

    @Column(columnDefinition = "text")
    private String observaciones;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInscripcionId() { return inscripcionId; }
    public void setInscripcionId(UUID inscripcionId) { this.inscripcionId = inscripcionId; }
    public boolean isAsistio() { return asistio; }
    public void setAsistio(boolean asistio) { this.asistio = asistio; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(Instant fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
