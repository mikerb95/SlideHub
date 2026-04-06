-- V10: Estado de conversión PPTX via Lambda
-- NULL = no aplica (presentaciones DRIVE/UPLOAD normales)
-- PROCESSING = Lambda procesando; READY = slides disponibles; FAILED = error
ALTER TABLE presentations ADD COLUMN pptx_status VARCHAR(20);
