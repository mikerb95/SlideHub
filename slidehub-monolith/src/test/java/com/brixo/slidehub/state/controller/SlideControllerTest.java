package com.brixo.slidehub.state.controller;

import com.brixo.slidehub.state.model.SlideStateResponse;
import com.brixo.slidehub.state.service.SlideStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlideController.class)
class SlideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlideStateService slideStateService;

    @Test
    void getSlide_whenNoState_returnsDefaultSlideAndTotal() throws Exception {
        given(slideStateService.getCurrentSlide()).willReturn(new SlideStateResponse(1, 11));

        mockMvc.perform(get("/api/slide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slide").value(1))
                .andExpect(jsonPath("$.totalSlides").value(11));
    }

    @Test
    void setSlide_whenValidPayload_returnsUpdatedSlideState() throws Exception {
        given(slideStateService.setSlide(3, 12)).willReturn(new SlideStateResponse(3, 12));

        mockMvc.perform(post("/api/slide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slide\":3,\"totalSlides\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slide").value(3))
                .andExpect(jsonPath("$.totalSlides").value(12));
    }
}
