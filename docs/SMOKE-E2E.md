# Smoke E2E local (Monolito)

Script de verificación rápida para validar que `slidehub-monolith` levanta correctamente y responde en rutas públicas/API críticas sin depender de proveedores externos de IA.

## Ejecutar

Desde la raíz del repo:

```bash
./scripts/smoke-e2e.sh
```

## Qué valida

- Monolito:
  - `GET /actuator/health`
  - `GET /showcase`
  - `GET /api/ai/notes/health`
  - `GET/POST /api/slide`
  - `POST /api/haptics/events/publish`
  - `GET /api/haptics/events/next`
  - `GET /status/api/checks`
  - `GET /slides`, `GET /demo`, `GET /remote`

## Dependencias

- `docker`
- `curl`
- Maven wrapper (`./mvnw`)

## Notas

- El script crea contenedores temporales de Redis y Mongo.
- Para smoke local/CI, fuerza:
  - `SPRING_FLYWAY_ENABLED=false`
  - `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
  - H2 en memoria (`DATABASE_URL`)
- Logs quedan en `target/smoke-logs/`.

## Variables opcionales

- `KEEP_SMOKE_CONTAINERS=true` para no eliminar Redis/Mongo al finalizar.
- `SMOKE_PORT=8080` para cambiar puerto base del smoke.

## Ejecutar desde GitHub Actions (manual)

- Workflow: `.github/workflows/smoke-e2e.yml`
- Trigger: **Actions → Smoke E2E Monolith → Run workflow**
- Artifact al finalizar: `smoke-logs` (contenido de `target/smoke-logs/`)

## Nota legacy

La variante histórica de smoke por microservicios queda deprecada. Si se requiere, debe ejecutarse en rama legacy o con un script separado.
