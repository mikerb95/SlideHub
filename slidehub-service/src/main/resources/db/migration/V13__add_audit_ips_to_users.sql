-- Migración para añadir campos de IP de auditoría a la tabla de usuarios
-- AGENTS.md §2.2 (Seguridad y Auditoría)

ALTER TABLE users 
ADD COLUMN registration_ip VARCHAR(45) DEFAULT NULL,
ADD COLUMN last_login_ip VARCHAR(45) DEFAULT NULL;

COMMENT ON COLUMN users.registration_ip IS 'Dirección IP utilizada durante la creación de la cuenta (IPv4 o IPv6)';
COMMENT ON COLUMN users.last_login_ip IS 'Última dirección IP registrada durante un inicio de sesión exitoso';
