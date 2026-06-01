-- =============================================================================
-- V2: Evolución del schema de outbox_events para inscription-service
--
-- Motivo: paridad con payment-service (Prompt 3) y con el modelo de dominio
--         compartido EstadoOutbox / OutboxEvent (Prompt 2 del SAD).
--
-- Cambios:
--   1. Agregar aggregate_type  → trazabilidad del tipo de agregado emisor
--   2. Reemplazar published (BOOLEAN) por estado (VARCHAR/enum)
--   3. Renombrar columnas a español para consistencia con el dominio
--      published_at → enviado_en
--      created_at   → creado_en
--   4. Agregar intentos        → contador de reintentos de publicación
--   5. Re-crear índice parcial con nuevos nombres
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. aggregate_type: tipo del agregado que generó el evento.
--    Backfill: inscription-service solo emite eventos del agregado Inscripcion.
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN aggregate_type VARCHAR(100);
UPDATE outbox_events SET aggregate_type = 'Inscripcion' WHERE aggregate_type IS NULL;
ALTER TABLE outbox_events ALTER COLUMN aggregate_type SET NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. Reemplazar published BOOLEAN por estado VARCHAR(20)
--    PENDIENTE = aún no publicado en RabbitMQ
--    ENVIADO   = publicado exitosamente
--    FALLIDO   = agotados los intentos de reintento
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN estado VARCHAR(20);
UPDATE outbox_events
   SET estado = CASE WHEN published = TRUE THEN 'ENVIADO' ELSE 'PENDIENTE' END;
ALTER TABLE outbox_events ALTER COLUMN estado SET NOT NULL;
ALTER TABLE outbox_events DROP COLUMN published;

-- -----------------------------------------------------------------------------
-- 3. Renombrar columnas a español
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events RENAME COLUMN published_at TO enviado_en;
ALTER TABLE outbox_events RENAME COLUMN created_at   TO creado_en;

-- -----------------------------------------------------------------------------
-- 4. Agregar intentos: contador de intentos fallidos de publicación.
--    inscription-service no tenía esta columna en V1.
--    DEFAULT 0 para filas existentes.
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN intentos INTEGER NOT NULL DEFAULT 0;

-- Comentarios de columna
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Tipo del agregado emisor: Inscripcion';
COMMENT ON COLUMN outbox_events.estado         IS 'Estado: PENDIENTE, ENVIADO, FALLIDO';
COMMENT ON COLUMN outbox_events.intentos       IS 'Contador de intentos de publicación fallidos';
COMMENT ON COLUMN outbox_events.enviado_en     IS 'Timestamp de publicación exitosa en RabbitMQ';
COMMENT ON COLUMN outbox_events.creado_en      IS 'Timestamp de creación del evento de dominio';

-- -----------------------------------------------------------------------------
-- 5. Re-crear índice parcial para el relay con los nuevos nombres de columna
--    El índice anterior (idx_outbox_pending) queda inválido porque cubría
--    la columna published (eliminada) y created_at (renombrada).
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_outbox_pending;

CREATE INDEX idx_outbox_estado_creado ON outbox_events(estado, creado_en)
    WHERE estado = 'PENDIENTE';
