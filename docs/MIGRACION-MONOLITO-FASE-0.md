# Migración a Monolito — Fase 0 (Bootstrap)

Fecha: 2026-04-03
Rama: `development`

## Objetivo
Crear la base técnica del monolito sin romper el repo existente, habilitando compilación independiente y arranque inicial sobre `slidehub-monolith`.

## Cambios realizados

### 1) Nuevo módulo monolito

Se creó:

- `slidehub-monolith/pom.xml`

Incluye dependencias consolidadas para:

- Web MVC + Thymeleaf
- Security + OAuth2
- JPA + Flyway + PostgreSQL
- Redis + MongoDB
- WebClient (WebFlux)
- AWS S3 SDK v2
- Testing (`spring-boot-starter-test`, `spring-security-test`)

### 2) Integración al parent Maven

Se actualizó:

- `pom.xml` (raíz)

Cambio:

- se añadió el módulo `slidehub-monolith` al `<modules>` del agregador.

### 3) Bootstrap de aplicación monolítica

Se creó:

- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/SlideHubMonolithApplication.java`

Características:

- `@SpringBootApplication(scanBasePackages = "com.brixo.slidehub")`
- `@EnableScheduling`
- `@EnableConfigurationProperties` para rate limit

### 4) Port inicial de rate limiting (desde gateway)

Se crearon:

- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitDecision.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitProperties.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitFilter.java`

Objetivo:

- conservar protección anti-abuso para endpoints sensibles de IA en arquitectura monolítica.

### 5) Configuración unificada base

Se creó:

- `slidehub-monolith/src/main/resources/application.properties`

Incluye:

- puerto `8080`
- datasource/flyway
- redis/mongodb
- oauth2 github/google
- integración IA (Gemini/Groq)
- resend/s3
- reglas de rate limit portadas
- actuator health/info

## Verificación

Compilación del nuevo módulo:

```bash
./mvnw clean compile -pl slidehub-monolith -am
```

Resultado esperado: `BUILD SUCCESS`.

## Resultado de fase

- Módulo monolito creado y compilando.
- Base técnica lista para migrar dominios funcionales (`state`, `ai`, `ui`).
- El monorepo mantiene servicios legacy sin bloquear la transición.

## Próximo paso sugerido (Fase 1)

1. Migrar código de `state-service`, `ai-service` y `ui-service` dentro de `slidehub-monolith`.
2. Copiar recursos `templates`, `static`, `db/migration`.
3. Mantener rutas públicas existentes para compatibilidad frontend.
