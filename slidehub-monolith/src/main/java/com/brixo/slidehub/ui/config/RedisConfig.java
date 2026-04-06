package com.brixo.slidehub.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configura Redis explícitamente para soportar:
 * 1. REDIS_HOST con formato "hostname:port" (Redis Cloud, Upstash)
 * 2. Autenticación via REDIS_PASSWORD (NOAUTH)
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {

        String host = redisHost.trim();
        int port = redisPort;

        // Formato host:port en REDIS_HOST (e.g., redis-12732.c331.us-west1-1.gce.cloud.redislabs.com:12732)
        int colonIndex = host.lastIndexOf(':');
        if (colonIndex > 0) {
            String portPart = host.substring(colonIndex + 1);
            try {
                port = Integer.parseInt(portPart);
                host = host.substring(0, colonIndex);
                log.info("Parsed Redis host:port from REDIS_HOST → {}:{}", host, port);
            } catch (NumberFormatException ignored) {
                // No era un puerto — usar el host tal cual
            }
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);

        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(RedisPassword.of(redisPassword));
            log.info("Redis password configured from REDIS_PASSWORD");
        }

        log.info("Redis configured for {}:{}", host, port);
        return new LettuceConnectionFactory(config);
    }
}
