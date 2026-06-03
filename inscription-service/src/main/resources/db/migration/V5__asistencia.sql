CREATE TABLE IF NOT EXISTS asistencia (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inscripcion_id   UUID NOT NULL UNIQUE REFERENCES inscripcion(id),
    asistio          BOOLEAN NOT NULL DEFAULT false,
    fecha_registro   TIMESTAMP NOT NULL DEFAULT NOW(),
    registrado_por   VARCHAR(255) NOT NULL,
    observaciones    TEXT
);

CREATE INDEX IF NOT EXISTS idx_asistencia_inscripcion ON asistencia(inscripcion_id);
