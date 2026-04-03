# Migración a Monolito — Fase 3 (Deploy + Documentación)

Fecha: 2026-04-03
Rama: `development`

## Objetivo
Consolidar despliegue y documentación para que el camino principal del proyecto sea monolítico, eliminando la guía operativa basada en 4 microservicios.

## Cambios realizados

### 1) Dockerfile del monolito

Se creó:

- `slidehub-monolith/Dockerfile`

Características:

- Multi-stage (`jdk` builder + `jre` runtime)
- Build del módulo `slidehub-monolith`
- Healthcheck a `/actuator/health`
- Puerto `8080`

### 2) Blueprint de Render unificado

Se actualizó:

- `render.yaml`

Ahora despliega un único servicio:

- `slidehub-monolith`

Se eliminaron servicios separados (`gateway`, `state`, `ui`, `ai`) del blueprint principal.

### 3) README monolito-first

Se reescribió:

- `README.md`

Nuevos ejes:

- Arquitectura actual = monolito modular
- Instrucciones de ejecución local con `slidehub-monolith`
- Resumen API y despliegue orientado a una sola app
- Nota de legado para módulos microservicio históricos

### 4) Guía de despliegue monolito

Se reescribió:

- `DEPLOYMENT.md`

Incluye:

- Flujo Blueprint en Render
- Variables de entorno consolidadas
- Callback OAuth2
- Verificación post-deploy
- Troubleshooting actualizado a monolito

### 5) Configuración base URL

Se añadió propiedad:

- `slidehub-monolith/src/main/resources/application.properties`
  - `slidehub.base-url=${BASE_URL:http://localhost:8080}`

Esto normaliza generación de URLs/callbacks en entorno productivo.

## Verificación

Compilación ejecutada:

```bash
./mvnw clean compile -pl slidehub-monolith -am
```

Resultado esperado: `BUILD SUCCESS`.

## Resultado de fase

- Despliegue recomendado en Render: **1 servicio monolítico**.
- Documentación principal alineada al estado actual de arquitectura.
- Referencias legacy de microservicios permanecen solo como contexto histórico.

## Próximo paso sugerido

Fase 4:

1. Ajustar pipelines CI/CD para ejecutar pruebas focalizadas en `slidehub-monolith`.
2. Añadir smoke E2E monolito en `docs/SMOKE-E2E.md`.
3. Definir checklist de corte para retirar oficialmente rutas de trabajo microservicio en documentación secundaria.
