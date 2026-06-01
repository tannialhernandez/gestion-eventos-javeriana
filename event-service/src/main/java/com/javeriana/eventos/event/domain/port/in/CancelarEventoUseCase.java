package com.javeriana.eventos.event.domain.port.in;

import java.util.UUID;

public interface CancelarEventoUseCase {

    void cancelar(UUID eventoId, UUID solicitanteId, String motivo);
}
