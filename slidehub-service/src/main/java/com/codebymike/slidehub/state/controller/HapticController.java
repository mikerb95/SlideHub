package com.codebymike.slidehub.state.controller;

import com.codebymike.slidehub.state.model.PublishHapticRequest;
import com.codebymike.slidehub.state.service.HapticEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API REST para eventos hapticos entre participantes de una reunion (HU-024, HU-025).
 *
 * POST /api/haptics/events/publish → publica un patron haptico a un participante
 * GET  /api/haptics/events/next    → consume el siguiente evento pendiente (long-polling)
 */
@RestController
@RequestMapping("/api/haptics/events")
public class HapticController {

    private final HapticEventService hapticEventService;

    public HapticController(HapticEventService hapticEventService) {
        this.hapticEventService = hapticEventService;
    }

    /**
     * Publica un evento haptico dirigido a un participante especifico.
     *
     * @param request cuerpo con participantToken, patron de vibracion y mensaje opcional
     * @return 200 con {@code {"success": true}} si se publico correctamente,
     *         400 si el token o el patron son invalidos
     */
    @PostMapping("/publish")
    public ResponseEntity<?> publish(@RequestBody PublishHapticRequest request) {
        try {
            hapticEventService.publish(request.participantToken(), request.pattern(), request.message());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Consume y retorna el siguiente evento haptico pendiente para el participante.
     *
     * @param participantToken token unico del participante que consulta sus eventos
     * @return 200 con el evento haptico si existe, 204 No Content si no hay eventos pendientes
     */
    @GetMapping("/next")
    public ResponseEntity<?> next(@RequestParam("participantToken") String participantToken) {
        return hapticEventService.popNext(participantToken)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
