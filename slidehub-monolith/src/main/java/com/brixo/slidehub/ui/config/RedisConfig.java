package com.brixo.slidehub.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configura Redis explícitamente para soportar el formato host:port en REDIS_HOST.
 *
 * Plataformas como Redis Cloud proveen el DSN con puerto incluido en el host:
 *   REDIS_HOST=redis-12732.c331.us-west1-1.gce.cloud.redislabs.com:12732
 *
 * Spring Boot lee REDIS_HOST y REDIS_PORT por separado. Si REDIS_HOST ya
 * incluye el puerto, Spring intenta conectar a "hostname:port:6379" → DNS falla
 * con el error típico <unresolved>:6379.
 *
 * Esta configuración parsea el host y extrae el puerto si viene incluido.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {

        String host = redisHost.trim();
        int port = redisPort;

        // Formato host:port en REDIS_HOST (e.g., Redis Cloud, Upstash)
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > 0) {
            String portPart = host.substring(colonIndex + 1);
            try {
                port = Integer.parseInt(portPart);
                host = host.substring(0, colonIndex);
                log.info("Parsed Redis host:port from REDIS_HOST env var → {}:{}", host, port);
            } catch (NumberFormatException ignored) {
                // No era un puerto — usar el host tal cual
            }
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        log.info("Redis configured for {}:{}", host, port);
        return new LettuceConnectionFactory(config);
    }
}
