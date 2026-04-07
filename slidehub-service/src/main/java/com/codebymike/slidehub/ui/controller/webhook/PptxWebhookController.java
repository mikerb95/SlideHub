package com.codebymike.slidehub.ui.controller.webhook;

import com.codebymike.slidehub.ui.service.PresentationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/pptx-conversion")
public class PptxWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PptxWebhookController.class);

    private final PresentationService presentationService;
    private final String webhookSecret;

    public PptxWebhookController(
            PresentationService presentationService,
            @Value("${slidehub.webhook.secret:CHANGE_ME_IN_PROD}") String webhookSecret) {
        this.presentationService = presentationService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<String> handlePptxConversion(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String providedSecret,
            @RequestBody Map<String, Object> payload) {

        // 1. Autenticación — comparación en tiempo constante para evitar timing attacks
        if (providedSecret == null || !MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                webhookSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Intento de webhook no autorizado o sin secreto");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Secret");
        }

        // 2. Extraer Payload
        String presentationId = (String) payload.get("presentationId");
        String status = (String) payload.get("status");
        Integer slideCount = payload.get("slideCount") instanceof Number n ? n.intValue() : null;
        String error = (String) payload.get("error");

        log.info("Webhook recibido para presentación {}: status={}, slides={}", presentationId, status, slideCount);

        if (presentationId == null || presentationId.isBlank()) {
            return ResponseEntity.badRequest().body("Falta presentationId");
        }

        // 3. Procesar estado
        try {
            if ("READY".equals(status) && slideCount != null && slideCount > 0) {
                presentationService.initializeSlidesAndMarkReady(presentationId, slideCount);
                return ResponseEntity.ok("Procesado con éxito");
            } else {
                log.error("Falló la conversión PPTX en Lambda para {}. Motivo: {}", presentationId, error);
                presentationService.markAsFailed(presentationId);
                return ResponseEntity.ok("Fallo registrado.");
            }
        } catch (Exception ex) {
            log.error("Error procesando webhook para {}: {}", presentationId, ex.getMessage());
            return ResponseEntity.internalServerError().body("Error interno: " + ex.getMessage());
        }
    }
}
