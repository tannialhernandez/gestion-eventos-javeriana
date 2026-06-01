package com.javeriana.eventos.payment.domain.port.out;

import java.time.Instant;

public interface OutboxRetentionRepository {
    int eliminarEnviadosAnterioresA(Instant umbral, int batchSize);
    int eliminarFallidosAnterioresA(Instant umbral, int batchSize);
    int eliminarMensajesProcesadosAnterioresA(Instant umbral, int batchSize);
}
