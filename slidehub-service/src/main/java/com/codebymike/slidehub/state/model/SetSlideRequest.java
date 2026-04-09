package com.codebymike.slidehub.state.model;

/** Request body para POST /api/slide */
public record SetSlideRequest(int slide, Integer totalSlides, String presentationId) {
}
