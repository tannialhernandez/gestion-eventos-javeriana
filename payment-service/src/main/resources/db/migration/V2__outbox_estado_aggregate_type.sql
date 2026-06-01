-- =============================================================================
-- V2: Evolución del schema de outbox_events
--
-- Motivo: auditoría Entrega 3 — hallazgos #1 y #9 del modelo de dominio
--
-- Cambios:
--   1. Agregar aggregate_type  → trazabilidad del tipo de agregado emisor
--   2. Reemplazar published (BOOLEAN) por estado (VARCHAR/enum)
--      → permite PENDIENTE / ENVIADO / FALLIDO
--   3. Renombrar columnas a español para consistencia con el dominio
--      published_at → enviado_en
--      attempts     → intentos
--      created_at   → creado_en
--   4. Re-crear índice parcial con nuevos nombres
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. aggregate_type: identifica el tipo de agregado que generó el evento
--    (ej: "Pago" en payment-service, "Inscripcion" en inscription-service)
--    Backfill seguro: payment-service solo emite eventos del agregado Pago.
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN aggregate_type VARCHAR(100);
UPDATE outbox_events SET aggregate_type = 'Pago' WHERE aggregate_type IS NULL;
ALTER TABLE outbox_events ALTER COLUMN aggregate_type SET NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. Reemplazar published BOOLEAN por estado VARCHAR(20)
--    Migración reversible: el UPDATE convierte el booleano al enum string.
--    PENDIENTE = aún no publicado en RabbitMQ
--    ENVIADO   = publicado exitosamente
--    FALLIDO   = agotados los intentos de reintento
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events ADD COLUMN estado VARCHAR(20);
UPDATE outbox_events
   SET estado = CASE WHEN published = TRUE THEN 'ENVIADO' ELSE 'PENDIENTE' END;
ALTER TABLE outbox_events ALTER COLUMN estado SET NOT NULL;

-- Eliminar columna booleana original — ya no es la fuente de verdad
ALTER TABLE outbox_events DROP COLUMN published;

-- -----------------------------------------------------------------------------
-- 3. Renombrar columnas a español para consistencia con el modelo de dominio
-- -----------------------------------------------------------------------------
ALTER TABLE outbox_events RENAME COLUMN published_at TO enviado_en;
ALTER TABLE outbox_events RENAME COLUMN attempts     TO intentos;
ALTER TABLE outbox_events RENAME COLUMN created_at   TO creado_en;

-- Añadir comentarios de columna actualizados
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Tipo del agregado que generó el evento: Pago, Inscripcion, etc.';
COMMENT ON COLUMN outbox_events.estado         IS 'Estado del evento: PENDIENTE, ENVIADO, FALLIDO';
COMMENT ON COLUMN outbox_events.intentos       IS 'Contador de intentos de publicación fallidos';
COMMENT ON COLUMN outbox_events.enviado_en     IS 'Timestamp de publicación exitosa en RabbitMQ';
COMMENT ON COLUMN outbox_events.creado_en      IS 'Timestamp de creación del evento de dominio';

-- -----------------------------------------------------------------------------
-- 4. Re-crear índice parcial optimizado para el relay
--    El índice anterior (idx_outbox_pending) queda inválido porque cubría
--    la columna published (eliminada) y created_at (renombrada).
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_outbox_pending;

CREATE INDEX idx_outbox_estado_creado ON outbox_events(estado, creado_en)
    WHERE estado = 'PENDIENTE';
