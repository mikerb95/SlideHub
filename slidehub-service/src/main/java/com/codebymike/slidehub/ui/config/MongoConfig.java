package com.codebymike.slidehub.ui.config;

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
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Configura MongoDB explícitamente desde MONGODB_URI.
 *
 * Problema 1: spring.data.mongodb.uri=${MONGODB_URI:} resuelve a cadena vacía
 * cuando no está seteada, y Spring Boot 4.x cae a localhost:27017.
 *
 * Problema 2: el MongoHealthIndicator de actuator ejecuta "hello" en la
 * base de datos "local", que Atlas bloquea. Con MongoDatabaseFactory explícito
 * que apunta a la base correcta (slidehub), el health check corre ahí.
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
        log.info("MongoDB configured from MONGODB_URI → cluster: {}", host);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database:slidehub}") String database) {
        log.info("MongoDatabaseFactory using database: {}", database);
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }
}
