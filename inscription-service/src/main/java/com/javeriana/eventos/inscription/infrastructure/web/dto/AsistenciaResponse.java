package com.javeriana.eventos.inscription.infrastructure.web.dto;

import com.javeriana.eventos.inscription.application.AsistenciaService;

import java.util.UUID;

public record AsistenciaResponse(
    UUID inscripcionId,
    UUID eventoId,
    UUID usuarioId,
    String participante,
    String estado,
    boolean asistio,
    String fechaRegistro,
    String registradoPor,
    String observaciones
) {
    public static AsistenciaResponse from(AsistenciaService.InscripcionAsistencia asistencia) {
        return new AsistenciaResponse(
            asistencia.inscripcionId(),
            asistencia.eventoId(),
            asistencia.usuarioId(),
            "Participante " + asistencia.usuarioId().toString().substring(0, 8),
            asistencia.estado(),
            asistencia.asistio(),
            asistencia.fechaRegistro(),
            asistencia.registradoPor(),
            asistencia.observaciones()
        );
    }
}
