package com.brixo.slidehub.ui.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configura Redis explícitamente para el monolito (Spring Boot 4.x / Lettuce 6.8+).
 *
 * Problema: Lettuce 6.8+ envía HELLO 3 (RESP3) al conectar por defecto.
 * Redis Cloud responde NOAUTH al recibir HELLO sin autenticación, aunque no
 * haya password. En los microservicios (Spring Boot 3.x / Lettuce 6.2.x) no
 * ocurría porque HELLO no se enviaba por defecto.
 *
 * Solución: forzar ProtocolVersion.RESP2 para reproducir el comportamiento anterior.
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

        // RESP2 evita que Lettuce 6.8+ envíe HELLO 3, reproduciendo el comportamiento
        // de Spring Boot 3.x donde la conexión funcionaba sin password
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .build();

        log.info("Redis configured for {}:{} (RESP2)", host, port);
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }
}
