package com.javeriana.eventos.inscription.domain.port.out;

import java.time.Instant;

public interface OutboxRetentionRepository {
    int eliminarEnviadosAnterioresA(Instant umbral, int batchSize);
    int eliminarFallidosAnterioresA(Instant umbral, int batchSize);
    int eliminarMensajesProcesadosAnterioresA(Instant umbral, int batchSize);
}
