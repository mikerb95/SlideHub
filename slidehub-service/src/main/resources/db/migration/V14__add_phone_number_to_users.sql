-- V14: Agrega teléfono de contacto al usuario (registro local).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(25);
