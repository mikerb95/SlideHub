package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.brixo.slidehub.ui.service.ViewerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de viewer efímero para el stream de una presentación.
 *
 * Todos son públicos (no requieren auth). El viewerToken actúa como credencial efímera.
 *
 * Flujo normal:
 *   1. JS de slides.html llama POST /join  → recibe viewerToken (guardado en sessionStorage)
 *   2. JS pollea GET /stats cada 5s
 *   3. JS envía POST /heartbeat cada 30s para mantener el TTL en Redis
 *   4. Al cerrar pestaña: beforeunload llama POST /leave
 */
@RestController
@RequestMapping("/api/presentations/{presentationId}/stream")
public class StreamController {

    private final PresentationSessionRepository sessionRepository;
    private final ViewerService viewerService;

    public StreamController(PresentationSessionRepository sessionRepository,
            ViewerService viewerService) {
        this.sessionRepository = sessionRepository;
        this.viewerService = viewerService;
    }

    record JoinRequest(String displayName) {}
    record ViewerTokenRequest(String viewerToken) {}
    record HandRequest(String viewerToken, boolean raised) {}

    /**
     * Une a un viewer a la sesión activa.
     * Si no hay sesión activa devuelve 404 — el widget Q&A queda inactivo.
     * Si displayName es null o vacío se usa "Anónimo" como placeholder en Redis.
     */
    @PostMapping("/join")
    public ResponseEntity<?> join(@PathVariable String presentationId,
            @RequestBody JoinRequest request) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> {
                    String viewerToken = UUID.randomUUID().toString().replace("-", "");
                    String name = (request.displayName() != null && !request.displayName().isBlank())
                            ? request.displayName().trim().substring(0, Math.min(request.displayName().trim().length(), 100))
                            : "";
                    viewerService.register(session.getId(), viewerToken, name);
                    return ResponseEntity.ok(Map.of(
                            "viewerToken", viewerToken,
                            "sessionId", session.getId()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Renueva el TTL del viewer en Redis. Debe llamarse cada ~30s.
     * Devuelve 204 si el viewerToken es válido, 404 si no.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@PathVariable String presentationId,
            @RequestBody ViewerTokenRequest request) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> {
                    viewerService.heartbeat(session.getId(), request.viewerToken());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Elimina al viewer de Redis. Llamado en beforeunload. */
    @PostMapping("/leave")
    public ResponseEntity<?> leave(@PathVariable String presentationId,
            @RequestBody ViewerTokenRequest request) {
        sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .ifPresent(session -> viewerService.leave(session.getId(), request.viewerToken()));
        return ResponseEntity.noContent().build();
    }

    /** Levanta o baja la mano del viewer. */
    @PostMapping("/hand")
    public ResponseEntity<?> hand(@PathVariable String presentationId,
            @RequestBody HandRequest request) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> {
                    if (!viewerService.isRegistered(session.getId(), request.viewerToken())) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "viewerToken inválido o expirado."));
                    }
                    viewerService.setHand(session.getId(), request.viewerToken(), request.raised());
                    return ResponseEntity.noContent().<Object>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Estadísticas de viewers en tiempo real.
     * Polleado desde slides.html cada 5s cuando hay sesión activa.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> stats(@PathVariable String presentationId) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> {
                    ViewerService.ViewerStats stats = viewerService.getStats(session.getId());
                    return ResponseEntity.ok(Map.of(
                            "viewerCount", stats.viewerCount(),
                            "handCount", stats.handCount()));
                })
                .orElse(ResponseEntity.ok(Map.of("viewerCount", 0, "handCount", 0)));
    }
}
