package com.brixo.slidehub.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Gestiona viewers efímeros del stream en Redis.
 *
 * Claves Redis:
 *   stream_viewers:{sessionId}  HASH  viewerToken → displayName
 *   stream_hands:{sessionId}    SET   viewerTokens con mano levantada
 *
 * Los viewers no persisten en PostgreSQL — existen solo mientras la sesión está activa.
 * TTL conservador de 3h (la sesión de reunión dura máximo 2h).
 */
@Service
public class ViewerService {

    private static final Logger log = LoggerFactory.getLogger(ViewerService.class);
    private static final Duration VIEWERS_TTL = Duration.ofHours(3);

    private final StringRedisTemplate redis;

    public ViewerService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record ViewerStats(long viewerCount, long handCount) {}

    /** Registra un viewer en la sesión. Idempotente: re-join sobreescribe el displayName. */
    public void register(String sessionId, String viewerToken, String displayName) {
        String key = viewersKey(sessionId);
        redis.opsForHash().put(key, viewerToken, displayName != null ? displayName : "");
        redis.expire(key, VIEWERS_TTL);
    }

    /** Renueva el TTL del hash de viewers. Llamado en cada heartbeat. */
    public void heartbeat(String sessionId, String viewerToken) {
        if (Boolean.TRUE.equals(redis.opsForHash().hasKey(viewersKey(sessionId), viewerToken))) {
            redis.expire(viewersKey(sessionId), VIEWERS_TTL);
        }
    }

    /** Elimina al viewer del hash y de las manos levantadas. */
    public void leave(String sessionId, String viewerToken) {
        redis.opsForHash().delete(viewersKey(sessionId), viewerToken);
        redis.opsForSet().remove(handsKey(sessionId), viewerToken);
    }

    /** Levanta o baja la mano de un viewer. */
    public void setHand(String sessionId, String viewerToken, boolean raised) {
        if (raised) {
            redis.opsForSet().add(handsKey(sessionId), viewerToken);
            redis.expire(handsKey(sessionId), VIEWERS_TTL);
        } else {
            redis.opsForSet().remove(handsKey(sessionId), viewerToken);
        }
    }

    /** Devuelve conteo de viewers activos y manos levantadas. */
    public ViewerStats getStats(String sessionId) {
        Long viewers = redis.opsForHash().size(viewersKey(sessionId));
        Long hands = redis.opsForSet().size(handsKey(sessionId));
        return new ViewerStats(
                viewers != null ? viewers : 0L,
                hands != null ? hands : 0L);
    }

    /** Verifica que el viewerToken pertenezca a la sesión activa. */
    public boolean isRegistered(String sessionId, String viewerToken) {
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(viewersKey(sessionId), viewerToken));
    }

    /** Limpia todas las claves Redis de la sesión al cerrarla. */
    public void cleanupSession(String sessionId) {
        redis.delete(viewersKey(sessionId));
        redis.delete(handsKey(sessionId));
    }

    private String viewersKey(String sessionId) {
        return "stream_viewers:" + sessionId;
    }

    private String handsKey(String sessionId) {
        return "stream_hands:" + sessionId;
    }
}
