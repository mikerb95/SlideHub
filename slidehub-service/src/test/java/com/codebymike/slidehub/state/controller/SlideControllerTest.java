package com.codebymike.slidehub.state.controller;

import com.codebymike.slidehub.state.model.SlideStateResponse;
import com.codebymike.slidehub.state.service.SlideStateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

class SlideControllerTest {

    private final SlideStateService slideStateService = mock(SlideStateService.class);
    private final SlideController slideController = new SlideController(slideStateService);

    @Test
    void getSlide_whenNoState_returnsDefaultSlideAndTotal() throws Exception {
        given(slideStateService.getCurrentSlide()).willReturn(new SlideStateResponse(1, 11));

        ResponseEntity<SlideStateResponse> response = slideController.getSlide();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slide()).isEqualTo(1);
        assertThat(response.getBody().totalSlides()).isEqualTo(11);
    }

    @Test
    void setSlide_whenValidPayload_returnsUpdatedSlideState() throws Exception {
        given(slideStateService.setSlide(3, 12, null)).willReturn(new SlideStateResponse(3, 12));

        ResponseEntity<SlideStateResponse> response = slideController
                .setSlide(new com.codebymike.slidehub.state.model.SetSlideRequest(3, 12, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slide()).isEqualTo(3);
        assertThat(response.getBody().totalSlides()).isEqualTo(12);
    }
}
