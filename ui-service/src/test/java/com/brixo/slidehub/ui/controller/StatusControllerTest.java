package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.StatusCheckItem;
import com.brixo.slidehub.ui.model.StatusChecksResponse;
import com.brixo.slidehub.ui.service.StatusChecksService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusChecksService statusChecksService;

    @Test
    void getStatusChecks_returnsExpectedJsonPayload() throws Exception {
        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        StatusChecksResponse response = new StatusChecksResponse(
                now,
                List.of(new StatusCheckItem("state-service", "ok", 34L, now, "HTTP 200")));

        given(statusChecksService.getChecks()).willReturn(response);

        mockMvc.perform(get("/status/api/checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").value("2026-04-01T10:00:00Z"))
                .andExpect(jsonPath("$.checks[0].name").value("state-service"))
                .andExpect(jsonPath("$.checks[0].status").value("ok"))
                .andExpect(jsonPath("$.checks[0].latencyMs").value(34))
                .andExpect(jsonPath("$.checks[0].detail").value("HTTP 200"));
    }
}
