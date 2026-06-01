-- =============================================================================
-- V3: Tabla para idempotencia de consumidores AMQP
--
-- Propósito: el PagoConfirmadoConsumer (y futuros consumers) verifican
-- si un messageId ya fue procesado antes de ejecutar lógica de negocio.
-- Esto evita doble-procesamiento ante reentregas del broker (at-least-once).
--
-- Patrón de uso:
--   BEFORE  → estaProcesado(message_id, consumer_grupo)
--   AFTER   → registrarProcesado(message_id, consumer_grupo, tipo_mensaje)
--   Ambas operaciones en la misma TX que la lógica de negocio.
--   Si la TX hace rollback, el registro también se revierte.
-- =============================================================================

CREATE TABLE mensaje_procesado (
    message_id     VARCHAR(255) NOT NULL,
    consumer_grupo VARCHAR(100) NOT NULL,
    tipo_mensaje   VARCHAR(100) NOT NULL,
    procesado_en   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mensaje_procesado PRIMARY KEY (message_id, consumer_grupo)
);

COMMENT ON TABLE  mensaje_procesado               IS 'Idempotencia de consumidores AMQP. Retención recomendada: 30 días (pendiente Prompt 18).';
COMMENT ON COLUMN mensaje_procesado.message_id    IS 'messageId del header AMQP — UUID del evento origen (seteado por OutboxRelayService)';
COMMENT ON COLUMN mensaje_procesado.consumer_grupo IS 'Identificador del consumer que procesó el mensaje (ej: confirmar-inscripcion)';
COMMENT ON COLUMN mensaje_procesado.tipo_mensaje  IS 'Tipo del mensaje procesado (ej: PAGO_CONFIRMADO)';
COMMENT ON COLUMN mensaje_procesado.procesado_en  IS 'Timestamp de procesamiento — base para limpieza periódica';

-- Índice para limpieza periódica de registros con más de 30 días
CREATE INDEX idx_mensaje_procesado_procesado_en
    ON mensaje_procesado (procesado_en);
