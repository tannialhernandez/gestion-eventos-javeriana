-- V4__pago_audit.sql — Audit log inmutable para Ley 1581 (Colombia)
-- Cada transición de estado de Pago produce una entrada.
-- El trigger bloquea UPDATE y DELETE para garantizar append-only.

CREATE TABLE IF NOT EXISTS pago_audit (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pago_id          UUID         NOT NULL,
    inscripcion_id   UUID         NOT NULL,
    estado_anterior  VARCHAR(20),
    estado_nuevo     VARCHAR(20)  NOT NULL,
    actor            VARCHAR(100) NOT NULL,
    motivo           VARCHAR(500),
    metadatos        TEXT,
    ocurrido_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_origen        VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_pago_audit_pago_id
    ON pago_audit (pago_id);

CREATE INDEX IF NOT EXISTS idx_pago_audit_ocurrido_en
    ON pago_audit (ocurrido_en);

-- Trigger que bloquea UPDATE y DELETE (append-only — Ley 1581 Art. 11)
CREATE OR REPLACE FUNCTION fn_pago_audit_readonly()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'pago_audit es inmutable: UPDATE/DELETE no permitidos (Ley 1581 Art. 11)';
END;
$$;

CREATE TRIGGER trg_pago_audit_no_update
    BEFORE UPDATE ON pago_audit
    FOR EACH ROW EXECUTE FUNCTION fn_pago_audit_readonly();

CREATE TRIGGER trg_pago_audit_no_delete
    BEFORE DELETE ON pago_audit
    FOR EACH ROW EXECUTE FUNCTION fn_pago_audit_readonly();
