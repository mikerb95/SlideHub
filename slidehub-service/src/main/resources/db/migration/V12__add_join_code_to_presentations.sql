-- V12: Añade código de unión de 5 dígitos a presentaciones.
-- Permite que el público acceda a modo espectador sin autenticación.

ALTER TABLE presentations ADD COLUMN join_code VARCHAR(5);

-- Genera códigos aleatorios de 5 dígitos para filas existentes.
UPDATE presentations
SET join_code = LPAD((10000 + FLOOR(RANDOM() * 90000))::INT::TEXT, 5, '0')
WHERE join_code IS NULL;

ALTER TABLE presentations ALTER COLUMN join_code SET NOT NULL;

CREATE UNIQUE INDEX uq_presentations_join_code ON presentations (join_code);
