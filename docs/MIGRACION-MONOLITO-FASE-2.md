# Migración a Monolito — Fase 2

Fecha: 2026-04-03
Rama: `development`

## Objetivo de esta fase
Eliminar dependencias internas de arquitectura de microservicios dentro de `slidehub-service`, alinear narrativa técnica/UI al modelo monolítico modular y dejar evidencia documental de los cambios.

## Cambios implementados

### 1) Terminología y narrativa en UI
Se actualizaron vistas para reflejar arquitectura monolítica modular:

- `slidehub-service/src/main/resources/templates/showcase.html`
  - Métrica principal cambia de **4 Microservicios** a **1 Monolito**.

- `slidehub-service/src/main/resources/templates/ai-guide.html`
  - Título y encabezados cambian de “microservicio IA” a “módulo IA”.
  - Se reemplaza la explicación de gateway + 4 servicios por diagrama de monolito modular.
  - Se ajustan ejemplos JSON (`summary`, `structure`, `ports`, `deploymentHints`) al nuevo modelo.
  - Se actualiza sección de endpoints/rate limit para backend único.

- `slidehub-service/src/main/resources/templates/calidad.html`
  - Redacción de madurez y PDCA cambia de microservicios a módulos.
  - Evidencia técnica apunta a rutas de `slidehub-service`.
  - Pruebas listadas como centralizadas en `slidehub-service`.

### 2) Limpieza de metadatos técnicos

- `slidehub-service/src/main/java/com/brixo/slidehub/ai/controller/NotesController.java`
  - Health now returns:
    - `service: slidehub-service`
    - `module: ai`

- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationNotesController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/NotesBridgeService.java`
  - Comentarios y descripciones adaptados de “puente entre servicios” a “orquestación in-process”.

### 3) Ajuste funcional de rutas de slides

- `slidehub-service/src/main/java/com/brixo/slidehub/state/service/SlideStateService.java`
  - Se añadieron candidatos de directorio para entorno monolito:
    - `./src/main/resources/static/slides`
    - `./slidehub-service/src/main/resources/static/slides`
  - Se mantienen rutas legacy para compatibilidad temporal durante transición.

## Verificación

Compilación del módulo monolito:

```bash
./mvnw clean compile -pl slidehub-service -am
```

Resultado: **BUILD SUCCESS**.

## Estado al cierre de Fase 2

- Arquitectura monolítica modular funcional y compilando.
- Terminología principal de vistas técnicas alineada al monolito.
- Dependencias internas de “service URL” ya retiradas en `KeepAliveService` y `StatusChecksService` (fase previa) y documentación consistente con ello.

## Próximos pasos sugeridos (Fase 3)

1. Consolidar `render.yaml` para despliegue de **un solo servicio**.
2. Añadir perfil de ejecución local exclusivo de monolito (`application-dev.properties` específico).
3. Reorganizar documentación raíz (`README.md`, `DEPLOYMENT.md`) para retirar instrucciones de 4 servicios como default.
4. Ejecutar smoke E2E orientado a monolito y actualizar `docs/SMOKE-E2E.md`.
