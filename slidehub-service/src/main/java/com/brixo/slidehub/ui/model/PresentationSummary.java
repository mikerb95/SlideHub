package com.brixo.slidehub.ui.model;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Resumen de presentación para listados (JSON).
 *
 * Devuelve solo los campos necesarios para la UI de lista,
 * sin cargar los slides ni los quick links.
 */
public record PresentationSummary(
        String id,
        String name,
        String description,
        String sourceType,
        int totalSlides,
        LocalDateTime createdAt,
        String joinCode) {
    public static PresentationSummary from(Presentation p) {
        LocalDateTime localDate = p.getCreatedAt();
        if (localDate != null) {
            localDate = localDate.atZone(ZoneId.systemDefault())
                                 .withZoneSameInstant(ZoneId.of("America/Bogota"))
                                 .toLocalDateTime();
        }
        return new PresentationSummary(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getSourceType().name(),
                p.getSlides().size(),
                localDate,
                p.getJoinCode());
    }
}
