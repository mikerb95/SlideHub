package com.brixo.slidehub.ui.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura MongoClient explícitamente desde MONGODB_URI.
 *
 * Problema: spring.data.mongodb.uri=${MONGODB_URI:} resuelve a cadena vacía
 * cuando MONGODB_URI no está en el entorno. Spring Boot 4.x trata la URI vacía
 * como "no configurada" y usa localhost:27017 como fallback.
 *
 * Esta configuración crea el MongoClient solo cuando MONGODB_URI tiene valor,
 * con logging claro del host al que se conecta.
 */
@Configuration
@ConditionalOnExpression("!'${spring.data.mongodb.uri:}'.isBlank()")
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean
    public MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        ConnectionString connectionString = new ConnectionString(uri);
        String host = connectionString.getHosts() != null && !connectionString.getHosts().isEmpty()
                ? connectionString.getHosts().get(0)
                : "unknown";
        log.info("MongoDB configured explicitly from MONGODB_URI → host: {}", host);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(settings);
    }
}
