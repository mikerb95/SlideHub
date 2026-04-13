package com.codebymike.slidehub.ui.model;

/**
 * DTO de respuesta que representa un slide importado.
 */
public record SlideInfo(int number, String filename, String s3Url, boolean quickSlide) {
}
