package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.model.StatusChecksResponse;
import com.codebymike.slidehub.ui.service.StatusChecksService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller de la pagina de estado del sistema.
 *
 * Sirve la vista /status con polling de checks de salud de los servicios,
 * y el endpoint REST /status/api/checks que devuelve el estado actual en JSON.
 */
@Controller
public class StatusController {

    private final StatusChecksService statusChecksService;

    @Value("${slidehub.poll.status.interval-ms:2000}")
    private int statusPollIntervalMs;

    public StatusController(StatusChecksService statusChecksService) {
        this.statusChecksService = statusChecksService;
    }

    /**
     * Renderiza la vista de estado con el intervalo de polling configurado.
     *
     * @param model modelo Thymeleaf al que se agrega pollIntervalMs
     * @return nombre de la plantilla "status"
     */
    @GetMapping("/status")
    public String statusView(Model model) {
        model.addAttribute("pollIntervalMs", statusPollIntervalMs);
        return "status";
    }

    /**
     * Devuelve el estado actual de todos los checks de salud del sistema.
     *
     * @return 200 con {@link StatusChecksResponse} que contiene el resultado de cada check
     */
    @GetMapping("/status/api/checks")
    @ResponseBody
    public ResponseEntity<StatusChecksResponse> getStatusChecks() {
        return ResponseEntity.ok(statusChecksService.getChecks());
    }
}
