# Migración a Monolito — Fase 1 (Consolidación funcional)

Fecha: 2026-04-03
Rama: `development`

## Objetivo
Consolidar funcionalidad core en `slidehub-service`, incorporando dominios `state`, `ai` y `ui`, y eliminar las primeras fronteras HTTP internas entre módulos.

## Cambios realizados

### 1) Migración de dominios Java

Se consolidaron paquetes dentro de `slidehub-service/src/main/java/com/brixo/slidehub/`:

- `state/**` (controllers, models, services)
- `ai/**` (controllers, models, repositories, services)
- `ui/**` (config, controllers, models, repositories, services, exception)

### 2) Migración de recursos

Se incorporaron en `slidehub-service/src/main/resources/`:

- `templates/**`
- `static/**`
- `db/migration/**`

Esto dejó operativa la capa web y la persistencia del monolito con assets y migraciones disponibles.

### 3) Compatibilidad de rutas

Se preservaron rutas funcionales existentes para evitar ruptura del frontend:

- Estado/hápticos: `/api/slide`, `/api/demo`, `/api/haptics/**`
- IA: `/api/ai/**`
- Presentaciones/reuniones: `/api/presentations/**`
- Vistas: `/slides`, `/remote`, `/demo`, `/presenter`, `/main-panel`, etc.

### 4) Eliminación inicial de HTTP interno (in-process)

Se refactorizaron servicios puente para invocación directa en el mismo proceso:

- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/NotesBridgeService.java`
  - de llamadas HTTP a `NotesService` / `RepoAnalysisService` / `PresenterNoteRepository`.

- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/AssistBridgeService.java`
  - de POST multipart interno a llamada directa `AssistService`.

- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/HapticBridgeService.java`
  - de POST interno a llamada directa `HapticEventService`.

## Verificación

Compilación del monolito tras consolidación:

```bash
./mvnw clean compile -pl slidehub-service -am
```

Resultado esperado: `BUILD SUCCESS`.

## Resultado de fase

- `slidehub-service` ya contiene la funcionalidad principal de UI + State + AI.
- Se removieron fronteras HTTP internas críticas entre módulos.
- Código y recursos base quedaron centralizados en una sola unidad de despliegue.

## Próximo paso sugerido (Fase 2)

1. Limpiar dependencias residuales de URLs internas (`state-service`/`ai-service`).
2. Adaptar `KeepAliveService` y `StatusChecksService` a modo monolito.
3. Alinear textos/documentación técnica de UI al nuevo modelo arquitectónico.
