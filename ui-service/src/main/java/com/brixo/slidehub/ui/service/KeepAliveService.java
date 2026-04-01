package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Mantiene activos los microservicios en Render (free tier) mientras haya
 * sesiones de presentación activas.
 *
 * Render hiberna servicios gratuitos tras 15 min sin tráfico HTTP entrante.
 * Este scheduler hace pings cada 5 minutos a state-service y ai-service
 * cuando hay al menos una sesión activa en la base de datos.
 *
 * ui-service y gateway-service se mantienen vivos por el tráfico de polling
 * de los participantes (assignment-check, slide, demo).
 */
@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final PresentationSessionRepository sessionRepository;
    private final AuthenticatedSessionTracker authenticatedSessionTracker;
    private final UserActivityTracker userActivityTracker;
    private final WebClient stateClient;
    private final WebClient aiClient;
    private final long userActivityWindowMs;

    public KeepAliveService(
            PresentationSessionRepository sessionRepository,
            AuthenticatedSessionTracker authenticatedSessionTracker,
            UserActivityTracker userActivityTracker,
            @Value("${slidehub.state-service.url}") String stateUrl,
            @Value("${slidehub.ai-service.url}") String aiUrl,
            @Value("${slidehub.keep-alive.user-activity-window-ms:300000}") long userActivityWindowMs) {
        this.sessionRepository = sessionRepository;
        this.authenticatedSessionTracker = authenticatedSessionTracker;
        this.userActivityTracker = userActivityTracker;
        this.stateClient = WebClient.builder().baseUrl(stateUrl).build();
        this.aiClient = WebClient.builder().baseUrl(aiUrl).build();
        this.userActivityWindowMs = userActivityWindowMs;
    }

    @Scheduled(fixedDelayString = "${slidehub.keep-alive.interval-ms:120000}", initialDelayString = "${slidehub.keep-alive.initial-delay-ms:60000}")
    public void ping() {
        boolean hasActivePresentationSession = sessionRepository.existsByActiveTrue();
        boolean hasAuthenticatedUiSession = authenticatedSessionTracker.hasAuthenticatedSessions();
        boolean hasRecentUserActivity = userActivityTracker.hasRecentActivity(userActivityWindowMs);

        if (!hasActivePresentationSession && !hasAuthenticatedUiSession && !hasRecentUserActivity) {
            return;
        }

        log.debug("Keep-alive: trigger activo (presentación={}, sesiónUI={}, usuarioReciente={}), enviando pings",
                hasActivePresentationSession, hasAuthenticatedUiSession, hasRecentUserActivity);

        stateClient.get().uri("/actuator/health")
                .retrieve().bodyToMono(Void.class)
                .doOnError(e -> log.warn("Keep-alive: state-service no responde — {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();

        aiClient.get().uri("/actuator/health")
                .retrieve().bodyToMono(Void.class)
                .doOnError(e -> log.warn("Keep-alive: ai-service no responde — {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
    }
}
