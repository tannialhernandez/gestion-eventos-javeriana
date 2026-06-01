\set ON_ERROR_STOP on

-- Datos determinísticos para pruebas K6.
-- Este script se ejecuta dentro de la red Docker de docker-compose.e2e.yml.

\echo 'Seeding event-service database'
\connect postgresql://eventos_user:eventos_pass@db-event:5432/eventos_event

BEGIN;

DELETE FROM tarifa
WHERE id BETWEEN '00000000-0000-0000-0001-000000000001'::uuid
             AND '00000000-0000-0000-0001-000000000100'::uuid;

DELETE FROM evento
WHERE id BETWEEN '00000000-0000-0000-0000-000000000001'::uuid
             AND '00000000-0000-0000-0000-000000000100'::uuid;

INSERT INTO evento (
    id, titulo, descripcion, tipo, modalidad, fecha_inicio, fecha_fin,
    fecha_limite_inscripcion, cupo_maximo, cupo_disponible, estado,
    organizador_id, fecha_creacion, version
)
SELECT
    ('00000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
    'Evento de carga ' || gs,
    'Descripcion evento ' || gs,
    'CONGRESO',
    'HIBRIDO',
    CURRENT_DATE + INTERVAL '30 days',
    CURRENT_DATE + INTERVAL '32 days',
    (CURRENT_DATE + INTERVAL '25 days')::timestamp,
    CASE WHEN gs = 10 THEN 10 ELSE 500 END,
    CASE WHEN gs = 10 THEN 10 ELSE 500 END,
    'PUBLICADO',
    ('aaaaaaaa-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
    NOW(),
    0
FROM generate_series(1, 100) AS gs;

INSERT INTO tarifa (
    id, evento_id, nombre, precio, moneda, aplica_a,
    fecha_inicio_vigencia, fecha_fin_vigencia, activa
)
SELECT
    ('00000000-0000-0000-0001-' || lpad(gs::text, 12, '0'))::uuid,
    ('00000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
    'Tarifa General',
    150000.00,
    'COP',
    'ESTUDIANTE_JAVERIANA',
    CURRENT_DATE - INTERVAL '10 days',
    CURRENT_DATE + INTERVAL '60 days',
    true
FROM generate_series(1, 100) AS gs;

COMMIT;

\echo 'Seeding inscription-service database'
\connect postgresql://eventos_user:eventos_pass@db-inscription:5432/eventos_inscription

BEGIN;

TRUNCATE TABLE inscripcion, outbox_events, mensaje_procesado RESTART IDENTITY;
DELETE FROM evento_cupo;

INSERT INTO evento_cupo (evento_id, cupo_disponible, cupo_maximo, version)
SELECT
    ('00000000-0000-0000-0000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE WHEN gs = 10 THEN 10 ELSE 500 END,
    CASE WHEN gs = 10 THEN 10 ELSE 500 END,
    0
FROM generate_series(1, 100) AS gs;

COMMIT;

\echo 'Resetting payment-service transient load-test data'
\connect postgresql://eventos_user:eventos_pass@db-payment:5432/eventos_payment

BEGIN;

-- pago_audit es append-only por Ley 1581; este seed no ejecuta DELETE/TRUNCATE sobre esa tabla.
TRUNCATE TABLE pago, outbox_events, mensaje_procesado RESTART IDENTITY;

COMMIT;

\echo 'Load-test seed completed'
