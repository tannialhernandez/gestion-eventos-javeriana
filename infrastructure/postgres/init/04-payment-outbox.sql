-- ======================================================================
-- payment-service — Tablas de infraestructura de mensajería y locking
-- ======================================================================
-- Propósito:
--   1. outbox_events: eventos de dominio pendientes de publicar en RabbitMQ
--      (Outbox Pattern, ADR-008).
--   2. shedlock: tabla de locks distribuidos para @Scheduled en entornos
--      multi-instancia (Distributed Locking, ADR-018).
--
-- ADRs que materializa:
--   ADR-008 Patrón Transactional Outbox — docs/adrs/ADR-018-*.md
--   ADR-018 Distributed Locking con ShedLock — docs/adrs/ADR-018-*.md
--
-- Convención de naming:
--   - idx_outbox_<servicio>_<propósito> para índices del outbox.
--   - Las columnas reflejan exactamente los campos de OutboxEventEntity.java.
--
-- Política de migración:
--   Idempotente: todos los statements usan IF NOT EXISTS.
--   El script puede re-ejecutarse contra una BD existente sin error.
-- ======================================================================

\connect eventos_payment

-- ─── outbox_events ───────────────────────────────────────────────────────────
--
-- Los eventos de dominio (PagoConfirmadoEvent, PagoReembolsadoEvent) se
-- persisten aquí DENTRO de la misma transacción de negocio que modifica el
-- agregado Pago. OutboxRelayService los lee periódicamente y los publica
-- a RabbitMQ de forma asíncrona, garantizando at-least-once delivery.
-- Si el servidor cae tras el COMMIT, el relay encuentra los eventos al reiniciar.

CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID         NOT NULL,        -- ID del agregado raíz (pago_id)
    event_type    VARCHAR(100) NOT NULL,         -- PAGO_CONFIRMADO | PAGO_REEMBOLSADO
    payload       TEXT         NOT NULL,         -- JSON serializado del evento de dominio
    published     BOOLEAN      NOT NULL DEFAULT false,  -- false = pendiente de publicar
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- timestamp UTC de creación
    published_at  TIMESTAMPTZ,                   -- NULL mientras no publicado en RabbitMQ
    attempts      INT          NOT NULL DEFAULT 0       -- reintentos fallidos (observabilidad)
);

-- Índice parcial: OutboxRelayService consulta WHERE published = false ORDER BY created_at.
-- El índice parcial excluye filas ya publicadas → lectura eficiente en tabla grande.
CREATE INDEX IF NOT EXISTS idx_outbox_payment_pending
    ON outbox_events (created_at ASC)
    WHERE published = false;

-- Índice de auditoría: permite consultar todos los eventos de un agregado específico.
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events (aggregate_id);

COMMENT ON TABLE outbox_events IS
    'Outbox Pattern (ADR-008): eventos de dominio de payment-service pendientes de publicar en RabbitMQ.';
COMMENT ON COLUMN outbox_events.published_at IS
    'NULL = evento no publicado en RabbitMQ. OutboxRelayService lo actualiza tras publish exitoso.';
COMMENT ON COLUMN outbox_events.attempts IS
    'Número de intentos fallidos de publicación. No bloquea reintentos; solo observabilidad operativa.';

-- ─── shedlock ─────────────────────────────────────────────────────────────────
--
-- Schema oficial de ShedLock 5.13.0 para PostgreSQL.
-- Ref: https://github.com/lukas-krecan/ShedLock#configure-lockprovider
-- ADR-018: OutboxRelayService usa @SchedulerLock(name = "payment-outbox-relay")
-- para garantizar que en un entorno multi-instancia (RNF-02), solo una réplica
-- ejecuta el relay en un instante dado, evitando publicación duplicada de eventos.

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,   -- cuándo expira el lock (lockAtMostFor)
    locked_at  TIMESTAMP    NOT NULL,   -- cuándo se adquirió el lock
    locked_by  VARCHAR(255) NOT NULL,   -- hostname:PID de la instancia que lo tiene
    PRIMARY KEY (name)
);

COMMENT ON TABLE shedlock IS
    'Distributed locking para @Scheduled (ADR-018). JdbcTemplateLockProvider de ShedLock 5.13.0.';
COMMENT ON COLUMN shedlock.locked_by IS
    'Identifica qué instancia del servicio mantiene el lock (útil para debugging en multi-replica).';
