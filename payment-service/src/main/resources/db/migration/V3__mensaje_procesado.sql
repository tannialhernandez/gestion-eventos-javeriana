-- =============================================================================
-- V3: Tabla para idempotencia entrante de mensajes AMQP
--
-- Propósito: cuando payment-service empiece a consumir eventos externos
-- (ej. InscripcionCreadaEvent de inscription-service), esta tabla evita
-- procesar el mismo messageId más de una vez ante redeliveries del broker.
--
-- Patrón: antes de procesar un mensaje @RabbitListener, verificar si
-- su messageId ya está en esta tabla. Si sí → descartar (DUPLICADO).
-- Si no → procesar y registrar.
-- =============================================================================

CREATE TABLE mensaje_procesado (
    message_id      VARCHAR(64)  NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mensaje_procesado PRIMARY KEY (message_id, consumer_group)
);

COMMENT ON TABLE mensaje_procesado IS
    'Registro de mensajes AMQP ya procesados. Garantiza idempotencia entrante: '
    'si el broker reentrega un mensaje, la segunda ejecución lo detecta y lo descarta.';

COMMENT ON COLUMN mensaje_procesado.message_id     IS 'messageId del header AMQP — debe ser UUID del evento origen';
COMMENT ON COLUMN mensaje_procesado.consumer_group IS 'Identifica qué listener procesó el mensaje (ej: confirmar-inscripcion)';
COMMENT ON COLUMN mensaje_procesado.event_type     IS 'Tipo de evento procesado (ej: INSCRIPCION_CREADA)';
COMMENT ON COLUMN mensaje_procesado.processed_at   IS 'Timestamp de procesamiento — para limpieza periódica de registros antiguos';

-- Índice para limpieza periódica de registros con más de 30 días
CREATE INDEX idx_mensaje_procesado_processed_at
    ON mensaje_procesado(processed_at);
