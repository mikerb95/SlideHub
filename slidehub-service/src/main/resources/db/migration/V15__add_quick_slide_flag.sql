-- V15: Marca qué slides fueron creados como "slide rápido" durante la presentación
ALTER TABLE slides ADD COLUMN is_quick_slide BOOLEAN NOT NULL DEFAULT false;
