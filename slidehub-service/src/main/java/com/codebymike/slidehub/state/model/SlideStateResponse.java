package com.codebymike.slidehub.state.model;

/**
 * Respuesta del endpoint GET /api/slide (HU-008).
 * El campo totalSlides refleja el conteo real del directorio de slides.
 * El campo presentationId es la presentación activa actualmente (puede ser null).
 */
public record SlideStateResponse(int slide, int totalSlides, String presentationId) {

    /** Constructor de compatibilidad sin presentationId. */
    public SlideStateResponse(int slide, int totalSlides) {
        this(slide, totalSlides, null);
    }
}
