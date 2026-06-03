package com.javeriana.eventos.event.domain.port.in;

import com.javeriana.eventos.event.domain.model.Evento;

import java.util.UUID;

public interface GestionarWorkflowEventoUseCase {

    Evento enviarARevision(UUID eventoId, UUID solicitanteId);

    Evento aprobar(UUID eventoId, UUID adminId);

    Evento rechazar(UUID eventoId, UUID adminId, String motivo);
}
