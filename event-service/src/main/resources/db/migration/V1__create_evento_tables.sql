-- V1: Esquema inicial de event-service

CREATE TABLE IF NOT EXISTS evento (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo                    VARCHAR(300) NOT NULL,
    descripcion               TEXT,
    tipo                      VARCHAR(30) NOT NULL,
    modalidad                 VARCHAR(20) NOT NULL,
    fecha_inicio              DATE NOT NULL,
    fecha_fin                 DATE NOT NULL,
    fecha_limite_inscripcion  TIMESTAMP NOT NULL,
    cupo_maximo               INTEGER NOT NULL CHECK (cupo_maximo > 0),
    cupo_disponible           INTEGER NOT NULL CHECK (cupo_disponible >= 0),
    url_imagen                VARCHAR(500),
    url_evento_virtual        VARCHAR(500),
    estado                    VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    organizador_id            UUID NOT NULL,
    fecha_creacion            TIMESTAMP NOT NULL DEFAULT NOW(),
    version                   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT cupo_consistente CHECK (cupo_disponible <= cupo_maximo)
);

CREATE INDEX idx_evento_estado ON evento(estado);
CREATE INDEX idx_evento_publicado ON evento(id, cupo_disponible) WHERE estado = 'PUBLICADO';

-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS espacio_fisico (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre           VARCHAR(100) NOT NULL,
    edificio         VARCHAR(100) NOT NULL,
    capacidad_maxima INTEGER NOT NULL CHECK (capacidad_maxima > 0),
    equipamiento     TEXT[],
    activo           BOOLEAN NOT NULL DEFAULT true
);

-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sesion (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id          UUID NOT NULL REFERENCES evento(id),
    titulo             VARCHAR(300) NOT NULL,
    descripcion        TEXT,
    tipo               VARCHAR(30) NOT NULL,
    fecha_hora_inicio  TIMESTAMP NOT NULL,
    fecha_hora_fin     TIMESTAMP NOT NULL,
    espacio_id         UUID REFERENCES espacio_fisico(id),
    url_transmision    VARCHAR(500),
    cupo_sesion        INTEGER,
    estado             VARCHAR(30) NOT NULL DEFAULT 'PROGRAMADA',
    CONSTRAINT fin_despues_inicio CHECK (fecha_hora_fin > fecha_hora_inicio)
);

CREATE INDEX idx_sesion_evento ON sesion(evento_id);

-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tarifa (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id              UUID NOT NULL REFERENCES evento(id),
    nombre                 VARCHAR(100) NOT NULL,
    precio                 DECIMAL(12,2) NOT NULL CHECK (precio >= 0),
    moneda                 CHAR(3) NOT NULL DEFAULT 'COP',
    aplica_a               VARCHAR(30) NOT NULL,
    fecha_inicio_vigencia  DATE NOT NULL,
    fecha_fin_vigencia     DATE NOT NULL,
    activa                 BOOLEAN NOT NULL DEFAULT true
);

-- Invariante: una tarifa activa por (evento, aplica_a) en un momento dado
-- (se valida en la capa de aplicación, ya que SQL no soporta fácilmente
--  constraint temporal parcial sin rangos)
CREATE INDEX idx_tarifa_evento ON tarifa(evento_id, aplica_a) WHERE activa = true;
