-- V2__outbox_events.sql — Transactional Outbox Pattern (ADR-011) + ShedLock
-- Replica exacta del esquema de inscription-service para paridad arquitectónica.

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos        INT          NOT NULL DEFAULT 0,
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW(),
    enviado_en      TIMESTAMP,
    ultimo_error    TEXT,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_estado_creado
    ON outbox_events (estado, creado_en)
    WHERE estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

COMMENT ON TABLE outbox_events IS
    'Transactional Outbox Pattern (ADR-011). Relay con SKIP LOCKED + Publisher Confirms.';

-- ShedLock: previene ejecuciones paralelas del relay en deploys multi-instancia
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
