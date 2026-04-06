-- V11: columna de imagen de perfil para usuarios OAuth2 (GitHub / Google).
-- Nullable porque usuarios locales no tienen imagen hasta que la suban.
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);
