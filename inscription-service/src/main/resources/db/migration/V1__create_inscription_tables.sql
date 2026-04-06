-- V1: Esquema inicial de inscription-service

-- Copia local de cupos de eventos (para SELECT FOR UPDATE local)
CREATE TABLE IF NOT EXISTS evento_cupo (
    evento_id        UUID PRIMARY KEY,
    cupo_disponible  INTEGER NOT NULL CHECK (cupo_disponible >= 0),
    cupo_maximo      INTEGER NOT NULL CHECK (cupo_maximo > 0),
    version          INTEGER NOT NULL DEFAULT 0
);

-- Inscripciones
CREATE TABLE IF NOT EXISTS inscripcion (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id             UUID NOT NULL,
    evento_id              UUID NOT NULL,
    tarifa_id              UUID NOT NULL,
    estado                 VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_PAGO',
    fecha_inscripcion      TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_expiracion_pago  TIMESTAMP,
    codigo_qr              VARCHAR(500),
    idempotency_key        UUID NOT NULL,
    version                INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_inscripcion_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_usuario_evento UNIQUE (usuario_id, evento_id)
);

CREATE INDEX idx_inscripcion_expiradas ON inscripcion(estado, fecha_expiracion_pago)
    WHERE estado = 'PENDIENTE_PAGO';

-- Outbox Pattern: eventos pendientes de publicar a RabbitMQ
CREATE TABLE IF NOT EXISTS outbox_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT NOT NULL,
    published     BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMP
);

-- Índice parcial: solo filas no publicadas para que el relay sea eficiente
CREATE INDEX idx_outbox_pending ON outbox_events(published, created_at)
    WHERE published = false;
