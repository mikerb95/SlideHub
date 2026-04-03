package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keep-alive lógico del monolito mientras haya actividad relevante.
 *
 * En modo monolítico ya no hay pings entre microservicios, pero conservamos
 * este scheduler como señal de actividad interna para observabilidad.
 */
@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final PresentationSessionRepository sessionRepository;
    private final AuthenticatedSessionTracker authenticatedSessionTracker;
    private final UserActivityTracker userActivityTracker;
    private final long userActivityWindowMs;

    public KeepAliveService(
            PresentationSessionRepository sessionRepository,
            AuthenticatedSessionTracker authenticatedSessionTracker,
            UserActivityTracker userActivityTracker,
            @Value("${slidehub.keep-alive.user-activity-window-ms:300000}") long userActivityWindowMs) {
        this.sessionRepository = sessionRepository;
        this.authenticatedSessionTracker = authenticatedSessionTracker;
        this.userActivityTracker = userActivityTracker;
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

        log.debug("Keep-alive monolith: trigger activo (presentación={}, sesiónUI={}, usuarioReciente={})",
                hasActivePresentationSession, hasAuthenticatedUiSession, hasRecentUserActivity);
    }
}
