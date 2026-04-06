package com.brixo.slidehub.ui.controller.webhook;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.service.PresentationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            
        // 1. Autenticación del Webhook
        if (providedSecret == null || !providedSecret.equals(this.webhookSecret)) {
            log.warn("Intento de webhook no autorizado o sin secreto");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Secret");
        }

        // 2. Extraer Payload
        String presentationId = (String) payload.get("presentationId");
        String status = (String) payload.get("status");
        Integer slideCount = (Integer) payload.get("slideCount");
        String error = (String) payload.get("error");

        log.info("Webhook recibido para presentación {}: status={}, slides={}", presentationId, status, slideCount);

        if (presentationId == null) {
            return ResponseEntity.badRequest().body("Falta presentationId");
        }

        // 3. Procesar estado
        if ("READY".equals(status) && slideCount != null && slideCount > 0) {
            // Nota arquitectónica: Aquí deberías inyectar el código para buscar la 
            // presentación y crear los objetos Slide (del 1 al slideCount) asociados a ella.
            // presentationService.initializeSlidesAndMarkReady(presentationId, slideCount);
            log.info("Éxito. La presentación {} ya tiene sus diapositivas procesadas en S3.", presentationId);
            return ResponseEntity.ok("Procesado con éxito");
        } else {
            // Hubo un error en la Lambda (ej. FAILED)
            log.error("Falló la conversión PPTX en Lambda para {}. Motivo: {}", presentationId, error);
            // presentationService.markAsFailed(presentationId, error);
            return ResponseEntity.ok("Fallo registrado en la BD.");
        }
    }
}
