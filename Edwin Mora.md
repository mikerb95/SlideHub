# Edwin Mora
## Guía de exposición (2 minutos) — Gateway y control de entrada

## 1. Rol en el proyecto
Responsable histórico de `gateway-service` y de la lógica de control de entrada/rate limiting durante la migración a monolito.

## 2. Qué cambió en tu parte (de microservicio a monolito)

### Antes
- `gateway-service` era un servicio independiente.
- Su trabajo principal era enrutar peticiones entre servicios internos (`ui`, `state`, `ai`).
- Además aplicaba protección anti-abuso.

### Ahora
- El enrutamiento interno entre servicios ya no es necesario, porque todo corre en `slidehub-monolith`.
- Se mantiene tu aporte clave: **control de entrada y rate limit**, ahora dentro del monolito.

## 3. Archivos que debes dominar para sustentar
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/SlideHubMonolithApplication.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitFilter.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitProperties.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitDecision.java`
- `docs/MIGRACION-MONOLITO-FASE-0.md`
- `docs/MIGRACION-MONOLITO-FASE-1.md`

## 4. Guion exacto para 2 minutos

### 0:00 – 0:30
“Mi responsabilidad era el Gateway. Antes teníamos un servicio dedicado a enrutar tráfico entre cuatro microservicios y a proteger endpoints sensibles.”

### 0:30 – 1:10
“Con la migración, ese rol cambió: el ruteo interno dejó de ser necesario porque UI, State y AI ahora viven en el mismo proceso. Lo que sí preservamos fue la capa de protección de entrada con rate limiting.”

### 1:10 – 1:40
“Esto se implementó en el monolito con `GatewayRateLimitFilter`, `GatewayRateLimitService` y configuración centralizada en `GatewayRateLimitProperties`.”

### 1:40 – 2:00
“En resumen: pasamos de una portería entre edificios a seguridad de acceso en un solo edificio. Menos complejidad operativa, misma protección en entrada.”

## 5. Guía de estudio rápida (20–30 min)
1. Lee `docs/MIGRACION-MONOLITO-FASE-0.md` y anota 3 cambios clave.
2. Revisa cómo se usa el filtro de rate limit en el monolito.
3. Ten clara la frase: “eliminamos proxy interno, no eliminamos seguridad de entrada”.
4. Practica explicar el cambio en una sola oración.

## 6. Preguntas que te pueden hacer
- **¿Se perdió seguridad al quitar gateway separado?**  
  No. La protección se movió al monolito y sigue activa con rate limiting.

- **¿Qué desapareció realmente?**  
  Desapareció la necesidad operativa del proxy interno entre servicios, no el control de entrada.

## 7. Conexión con el siguiente expositor
Cierra diciendo: “Yo explico la entrada del sistema; ahora Jerson muestra cómo quedó el manejo del estado de presentación dentro del monolito.”
