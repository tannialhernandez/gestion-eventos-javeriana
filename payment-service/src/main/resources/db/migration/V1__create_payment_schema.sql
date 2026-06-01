-- =============================================================================
-- V1: Schema inicial de payment-service
--
-- Entidades origen:
--   PagoEntity       → tabla pago
--   OutboxEventEntity → tabla outbox_events
--
-- Generado para: Spring Boot 3.2.4, Hibernate 6.4, PostgreSQL 15
-- Compatibilidad ddl-auto: validate verificada contra anotaciones JPA.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tabla: pago
-- Propósito: registro de cada intento de pago asociado a una inscripción de
-- evento académico. Un pago nace en INICIADO y avanza según la pasarela
-- (MercadoPago o Simulador) vía webhook procesado por ProcesarWebhookService.
-- -----------------------------------------------------------------------------
CREATE TABLE pago (
    id                     UUID          NOT NULL,
    inscripcion_id         UUID          NOT NULL,
    monto                  NUMERIC(12,2) NOT NULL,
    moneda                 VARCHAR(3)    NOT NULL,
    pasarela               VARCHAR(30)   NOT NULL,
    referencia_externa     VARCHAR(200),
    preferencia_externa_id VARCHAR(200),
    estado                 VARCHAR(20)   NOT NULL,
    fecha_creacion         TIMESTAMP     NOT NULL,
    fecha_confirmacion     TIMESTAMP,
    fecha_reembolso        TIMESTAMP,
    intentos_cobro         INTEGER       NOT NULL,
    metadatos_pasarela     TEXT,
    version                INTEGER       NOT NULL DEFAULT 0,

    CONSTRAINT pk_pago PRIMARY KEY (id),

    -- Idempotencia ante webhooks duplicados (RN-13, ADR-009):
    -- referencia_externa es el ID asignado por la pasarela al pago. Si el mismo
    -- webhook llega dos veces, la segunda inserción falla aquí antes de
    -- ProcesarWebhookService.buscarPorReferenciaExterna().
    CONSTRAINT uk_pago_referencia_externa UNIQUE (referencia_externa)
);

COMMENT ON TABLE  pago                          IS 'Pagos de inscripciones a eventos académicos. Máquina de estados: INICIADO→PROCESANDO→CONFIRMADO/FALLIDO; CONFIRMADO→REEMBOLSADO';
COMMENT ON COLUMN pago.inscripcion_id           IS 'FK lógica a inscription-service (servicios independientes — sin FK física entre BDs)';
COMMENT ON COLUMN pago.referencia_externa       IS 'ID del pago en la pasarela externa (MercadoPago payment_id o similar). UNIQUE garantiza idempotencia';
COMMENT ON COLUMN pago.preferencia_externa_id   IS 'ID de la preferencia/checkout creado en la pasarela (MercadoPago preference_id)';
COMMENT ON COLUMN pago.estado                   IS 'Enum EstadoPago: INICIADO, PROCESANDO, CONFIRMADO, FALLIDO, REEMBOLSADO';
COMMENT ON COLUMN pago.metadatos_pasarela       IS 'JSON crudo del webhook recibido — solo para auditoría, no se parsea en lógica de negocio';
COMMENT ON COLUMN pago.version                  IS 'Control de concurrencia optimista (@Version JPA). Se incrementa en cada UPDATE';

-- Índice para búsquedas por inscripción (ProcesarWebhookService.buscarPorInscripcionId)
CREATE INDEX idx_pago_inscripcion_id ON pago(inscripcion_id);

-- Índice parcial para deduplicación de webhooks (solo filas ya confirmadas)
CREATE INDEX idx_pago_referencia_externa ON pago(referencia_externa)
    WHERE referencia_externa IS NOT NULL;


-- -----------------------------------------------------------------------------
-- Tabla: outbox_events
-- Propósito: eventos de dominio (PagoConfirmado, PagoReembolsado) persistidos
-- en la misma transacción de negocio. OutboxRelayService los lee y publica en
-- RabbitMQ de forma asíncrona.
--
-- Garantía (ADR-011 Transactional Outbox Pattern):
--   - Si el servicio cae tras el COMMIT y antes de publicar → el relay reintenta.
--   - Si RabbitMQ está caído → los eventos permanecen aquí hasta recuperación.
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id            UUID         NOT NULL,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMP    NOT NULL,
    published_at  TIMESTAMP,
    attempts      INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

COMMENT ON TABLE  outbox_events             IS 'Outbox Pattern (ADR-011): cola persistente de eventos de dominio. Publicados a RabbitMQ por OutboxRelayService cada 2s';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'UUID del agregado Pago que generó el evento';
COMMENT ON COLUMN outbox_events.event_type   IS 'Tipo canónico del evento: PAGO_CONFIRMADO, PAGO_REEMBOLSADO, PAGO_FALLIDO';
COMMENT ON COLUMN outbox_events.payload      IS 'JSON del DomainEvent con todos los campos requeridos por el consumidor (inscription-service)';
COMMENT ON COLUMN outbox_events.published    IS 'false = pendiente de publicar; true = ya enviado a RabbitMQ';
COMMENT ON COLUMN outbox_events.attempts     IS 'Intentos de publicación fallidos. Usado por lógica de reintentos y umbral de DLQ';

-- Índice para el relay (ADR-012): consulta periódica de eventos no publicados
-- ordenados por creación. El filtro parcial WHERE published = false hace que el
-- índice solo cubra la fracción de filas que el relay necesita leer.
CREATE INDEX idx_outbox_pending ON outbox_events(created_at)
    WHERE published = false;
