package com.codebymike.slidehub.ai.model;

/**
 * Request body para {@code POST /api/ai/notes/generate} (HU-016).
 *
 * @param presentationId ID de la presentación
 * @param slideNumber    Número de slide (1-based)
 * @param repoUrl        URL del repositorio GitHub (puede ser null)
 * @param imageData      Imagen del slide codificada en Base64 (puede ser null)
 * @param imageUrl       URL pública de la imagen en S3 (alternativa a
 *                       imageData; puede ser null)
 * @param slideContext   Descripción breve del slide como fallback si no hay
 *                       imagen
 * @param extraContext   Contexto textual adicional (README, pom.xml,
 *                       Dockerfile, etc.) cuando no hay acceso a GitHub
 */
public record GenerateNoteRequest(
                String presentationId,
                int slideNumber,
                String repoUrl,
                String imageData,
                String imageUrl,
                String slideContext,
                String extraContext) {
}
