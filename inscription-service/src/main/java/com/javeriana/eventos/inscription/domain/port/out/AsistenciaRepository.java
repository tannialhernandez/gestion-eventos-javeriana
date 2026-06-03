package com.javeriana.eventos.inscription.domain.port.out;

import com.javeriana.eventos.inscription.domain.model.Asistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsistenciaRepository {

    Asistencia guardar(Asistencia asistencia);

    Optional<Asistencia> buscarPorInscripcionId(UUID inscripcionId);

    List<Asistencia> buscarPorInscripcionIds(Collection<UUID> inscripcionIds);
}
