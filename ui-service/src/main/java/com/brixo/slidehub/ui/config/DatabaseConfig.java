package com.brixo.slidehub.ui.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transforma URLs de base de datos en formato libpq (postgres://...)
 * al formato JDBC requerido por HikariCP (jdbc:postgresql://...).
 *
 * Plataformas como Aiven, Heroku y Render suelen proveer el DSN en formato
 * libpq. El driver JDBC de PostgreSQL no acepta ese formato directamente.
 *
 * Ejemplos de transformación:
 * postgres://user:pass@host:port/db?sslmode=require
 * → jdbc:postgresql://host:port/db?user=user&password=pass&sslmode=require
 *
 * postgresql://user:pass@host:port/db
 * → jdbc:postgresql://host:port/db?user=user&password=pass
 *
 * Si la URL ya comienza con jdbc:, se usa tal cual (sin transformación).
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        String jdbcUrl = convertToJdbcUrl(databaseUrl);
        log.info("DataSource URL scheme: {}",
                jdbcUrl.substring(0, Math.min(jdbcUrl.indexOf("://") + 3, jdbcUrl.length())));

        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(jdbcUrl)
                .driverClassName(driverClassName);

        return builder.build();
    }

    /**
     * Convierte URL de formato libpq a JDBC si es necesario.
     * Si ya es jdbc:..., la retorna sin cambios.
     */
    String convertToJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL no puede estar vacía");
        }

        // Ya es formato JDBC — no tocar
        if (url.startsWith("jdbc:")) {
            return url;
        }

        // Formato libpq: postgres://user:pass@host:port/db?params
        // o: postgresql://user:pass@host:port/db?params
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return convertLibpqToJdbc(url);
        }

        // H2 u otros formatos de desarrollo
        return url;
    }

    private String convertLibpqToJdbc(String url) {
        // Quitar el esquema (postgres:// o postgresql://)
        String withoutScheme = url.replaceFirst("^postgres(ql)?://", "");

        String userInfo = null;
        String hostAndPath;

        // Extraer user:pass si existe
        int atIndex = withoutScheme.indexOf('@');
        if (atIndex != -1) {
            userInfo = withoutScheme.substring(0, atIndex);
            hostAndPath = withoutScheme.substring(atIndex + 1);
        } else {
            hostAndPath = withoutScheme;
        }

        // Construir la URL JDBC base
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
        jdbcUrl.append(hostAndPath);

        // Agregar credenciales como parámetros de query
        if (userInfo != null && !userInfo.isEmpty()) {
            String user;
            String password = null;

            int colonIndex = userInfo.indexOf(':');
            if (colonIndex != -1) {
                user = userInfo.substring(0, colonIndex);
                password = userInfo.substring(colonIndex + 1);
            } else {
                user = userInfo;
            }

            // Determinar si ya hay parámetros de query en la URL
            char separator = hostAndPath.contains("?") ? '&' : '?';
            jdbcUrl.append(separator).append("user=").append(user);
            if (password != null) {
                jdbcUrl.append("&password=").append(password);
            }
        }

        log.info("Converted libpq URL to JDBC format (host: {})",
                hostAndPath.substring(0, Math.min(hostAndPath.indexOf('/'), hostAndPath.length())));

        return jdbcUrl.toString();
    }
}
