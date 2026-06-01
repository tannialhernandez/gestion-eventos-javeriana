-- V3__fix_tarifa_moneda_type.sql
-- Corrects tarifa.moneda from CHAR(3) to VARCHAR(3) to match the JPA entity mapping.
-- CHAR(3) in PostgreSQL is stored as bpchar which fails Hibernate schema validation.
ALTER TABLE tarifa ALTER COLUMN moneda TYPE VARCHAR(3);
