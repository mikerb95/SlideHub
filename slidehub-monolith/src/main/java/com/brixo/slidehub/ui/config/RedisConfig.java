package com.brixo.slidehub.ui.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configura Redis explícitamente para el monolito (Spring Boot 4.x / Lettuce 6.8+).
 *
 * - RESP2: fuerza protocolo antiguo para evitar que Lettuce 6.8+ envíe HELLO 3
 * - host:port: parsea REDIS_HOST en formato "hostname:port" (Redis Cloud)
 * - password: lee REDIS_PASSWORD si está seteado (Redis Cloud siempre tiene password)
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

        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(host, port);
        if (redisPassword != null && !redisPassword.isBlank()) {
            standaloneConfig.setPassword(RedisPassword.of(redisPassword));
        }

        // RESP2: evita que Lettuce 6.8+ envíe HELLO 3 que provoca NOAUTH en algunos servidores
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .build();

        log.info("Redis configured for {}:{} (RESP2, password={})", host, port,
                (redisPassword != null && !redisPassword.isBlank()) ? "yes" : "no");
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }
}
