-- ============================================================
-- payment-service — tabla outbox_events
-- Outbox Pattern (ADR-11 del SAD):
--   Los eventos PagoConfirmado / PagoReembolsado se persisten
--   aquí dentro de la misma transacción de negocio.
--   OutboxRelayService los publica a RabbitMQ de forma asíncrona.
-- ============================================================

\connect eventos_payment

CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID        PRIMARY KEY,
    aggregate_id  UUID        NOT NULL,
    event_type    VARCHAR(100) NOT NULL,     -- PAGO_CONFIRMADO, PAGO_REEMBOLSADO
    payload       TEXT        NOT NULL,      -- JSON del evento de dominio
    published     BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ,              -- NULL = pendiente de publicar
    attempts      INT         NOT NULL DEFAULT 0  -- reintentos fallidos de publicación
);

-- Índice parcial: solo filas pendientes → lectura eficiente en el relay
-- (OutboxRelayService consulta WHERE published = false ORDER BY created_at)
CREATE INDEX IF NOT EXISTS idx_outbox_payment_pending
    ON outbox_events (created_at ASC)
    WHERE published = false;

-- Comentario de tabla para auditoría
COMMENT ON TABLE outbox_events IS
    'Outbox Pattern: eventos de dominio del payment-service pendientes de publicar en RabbitMQ. Ver ADR-11.';

COMMENT ON COLUMN outbox_events.attempts IS
    'Número de intentos fallidos de publicación. No limita reintentos — es solo observabilidad.';

COMMENT ON COLUMN outbox_events.published_at IS
    'NULL mientras el evento no fue publicado en RabbitMQ exitosamente.';
