package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

public interface PublicarEventoUseCase {
    void publicar(UUID eventoId, UUID solicitanteId);
}
