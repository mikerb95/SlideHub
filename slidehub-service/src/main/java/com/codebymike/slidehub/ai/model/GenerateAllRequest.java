package com.codebymike.slidehub.ai.model;

import java.util.List;

/**
 * Request body para {@code POST /api/ai/notes/generate-all} (PLAN-EXPANSION.md
 * Fase 3,
 * tarea 32).
 *
 * @param presentationId ID de la presentación
 * @param repoUrl        URL del repositorio GitHub (puede ser null)
 * @param extraContext   Contexto textual adicional cargado por el usuario
 *                       (README, pom.xml, Dockerfile, etc.)
 * @param slides         Lista de referencias a slides con sus URLs de imagen en
 *                       S3
 */
public record GenerateAllRequest(
                String presentationId,
                String repoUrl,
                String extraContext,
                List<SlideReference> slides) {
}
