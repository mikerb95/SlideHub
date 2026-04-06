# Migración a Monolito — Fase 4 (Smoke E2E)

Fecha: 2026-04-03
Rama: `development`

## Objetivo
Alinear validación rápida (local/CI) al modo monolítico, retirando el smoke basado en 4 servicios y dejando una ruta única de verificación.

## Cambios realizados

### 1) Documento de smoke actualizado

Se actualizó:

- `docs/SMOKE-E2E.md`

Ahora describe:

- flujo smoke para `slidehub-service`
- endpoints validados del backend único
- variables opcionales del script
- nota explícita de deprecación del smoke microservicios

### 2) Script de smoke monolito

Se actualizó:

- `scripts/smoke-e2e.sh`

Cambios clave:

- inicia Redis + Mongo temporales
- inicia solo `slidehub-service`
- usa `PORT` configurable (`SMOKE_PORT`)
- fuerza entorno smoke estable:
  - `SPRING_FLYWAY_ENABLED=false`
  - `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
  - H2 en memoria para smoke
- valida endpoints públicos/API críticos del monolito

### 3) Workflow de GitHub Actions alineado

Se actualizó:

- `.github/workflows/smoke-e2e.yml`

Cambios:

- nombre del workflow: `Smoke E2E Monolith`
- job renombrado a `smoke-e2e-monolith`
- ejecución directa de `./scripts/smoke-e2e.sh`

## Verificación

- Sintaxis bash del script:

```bash
bash -n ./scripts/smoke-e2e.sh
```

- Compilación del monolito:

```bash
./mvnw clean compile -pl slidehub-service -am
```

Resultado esperado: `BUILD SUCCESS`.

## Resultado de fase

- Smoke E2E principal ya está orientado al monolito.
- CI manual de smoke y documentación quedaron consistentes.
- Flujo legacy de microservicios marcado como deprecado para transición.
