package com.codebymike.slidehub.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Servicio de email via Resend API (CLAUDE.md §9.5.1).
 *
 * Integración HTTP pura con WebClient — sin JavaMail, Spring Mail ni Resend
 * SDK.
 * Endpoint: POST https://api.resend.com/emails
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final WebClient resendClient;

    @Value("${slidehub.resend.api-key}")
    private String apiKey;

    @Value("${slidehub.resend.from}")
    private String fromAddress;

    public EmailService() {
        this.resendClient = WebClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    /**
     * Envía un email HTML via Resend API.
     *
     * @param to      dirección de destino
     * @param subject asunto del mensaje
     * @param html    cuerpo HTML
     */
    public void send(String to, String subject, String html) {
        try {
            String responseBody = resendClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "from", fromAddress,
                            "to", List.of(to),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                    resp -> resp.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                            "Resend respondió con " + resp.statusCode() + ": " + body)))
                .bodyToMono(String.class)
                    .block();

            log.info("Email enviado vía Resend a {} con asunto '{}'. Respuesta: {}",
                to,
                subject,
                responseBody != null && !responseBody.isBlank() ? responseBody : "(sin cuerpo)");
        } catch (WebClientResponseException e) {
            log.error("Resend rechazó el email a {} con asunto '{}': status={}, body={}",
                to, subject, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Fallo al enviar email a {} (asunto: {}): {}", to, subject, e.getMessage());
        }
    }
}
