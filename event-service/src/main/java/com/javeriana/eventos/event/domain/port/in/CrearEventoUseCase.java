package com.javeriana.eventos.event.domain.port.in;

import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.model.ModalidadEvento;
import com.javeriana.eventos.event.domain.model.TipoEvento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface CrearEventoUseCase {

    Evento crear(Command command);

    record Command(
        String titulo,
        String descripcion,
        TipoEvento tipo,
        ModalidadEvento modalidad,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        LocalDateTime fechaLimiteInscripcion,
        int cupoMaximo,
        UUID organizadorId
    ) {}
}
