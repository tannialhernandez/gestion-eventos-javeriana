package com.javeriana.eventos.inscription.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Asistencia {

    private final UUID id;
    private final UUID inscripcionId;
    private final boolean asistio;
    private final Instant fechaRegistro;
    private final String registradoPor;
    private final String observaciones;

    public Asistencia(UUID id,
                      UUID inscripcionId,
                      boolean asistio,
                      Instant fechaRegistro,
                      String registradoPor,
                      String observaciones) {
        this.id = id;
        this.inscripcionId = inscripcionId;
        this.asistio = asistio;
        this.fechaRegistro = fechaRegistro;
        this.registradoPor = registradoPor;
        this.observaciones = observaciones;
    }

    public static Asistencia nueva(UUID inscripcionId,
                                   boolean asistio,
                                   String registradoPor,
                                   String observaciones) {
        return new Asistencia(UUID.randomUUID(), inscripcionId, asistio, Instant.now(),
            registradoPor, observaciones);
    }

    public Asistencia actualizar(boolean asistio, String registradoPor, String observaciones) {
        return new Asistencia(id, inscripcionId, asistio, Instant.now(), registradoPor, observaciones);
    }

    public UUID getId() { return id; }
    public UUID getInscripcionId() { return inscripcionId; }
    public boolean isAsistio() { return asistio; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public String getRegistradoPor() { return registradoPor; }
    public String getObservaciones() { return observaciones; }
}
