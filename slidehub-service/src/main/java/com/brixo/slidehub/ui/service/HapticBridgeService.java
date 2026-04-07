package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.state.service.HapticEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HapticBridgeService {

    private static final Logger log = LoggerFactory.getLogger(HapticBridgeService.class);

    private final HapticEventService hapticEventService;

    public HapticBridgeService(HapticEventService hapticEventService) {
        this.hapticEventService = hapticEventService;
    }

    public void publishSingle(String participantToken, String message) {
        publish(participantToken, "single", message);
    }

    public void publishTriple(String participantToken, String message) {
        publish(participantToken, "triple", message);
    }

    private void publish(String participantToken, String pattern, String message) {
        try {
            hapticEventService.publish(participantToken, pattern, message);
        } catch (Exception ex) {
            log.warn("No se pudo publicar evento háptico ({}) para token {}: {}",
                    pattern, participantToken, ex.getMessage());
        }
    }
}
