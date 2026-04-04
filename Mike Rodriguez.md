# Mike Rodriguez
## Guía de exposición (2 minutos) — Infraestructura y despliegue

## 1. Rol en el proyecto
Responsable de infraestructura y despliegue: Render, blueprint, variables de entorno, salud operativa y documentación de despliegue.

## 2. Qué cambió en tu parte (de operación distribuida a operación unificada)

### Antes
- Se operaban 4 servicios separados en Render.
- Había que coordinar health checks, puertos, variables y despliegues por servicio.
- Mayor complejidad diaria de operación.

### Ahora
- Se despliega un solo servicio principal: `slidehub-monolith`.
- Se unifica el punto de health check.
- Se simplifica la estrategia de build, deploy y troubleshooting.

## 3. Archivos que debes dominar para sustentar
- `render.yaml`
- `slidehub-monolith/Dockerfile`
- `DEPLOYMENT.md`
- `README.md`
- `docs/MIGRACION-MONOLITO-FASE-3-DEPLOY-DOCS.md`
- `docs/MIGRACION-MONOLITO-FASE-4-SMOKE.md`
- `scripts/smoke-e2e.sh`
- `.github/workflows/smoke-e2e.yml`

## 4. Guion exacto para 2 minutos

### 0:00 – 0:35
“Mi responsabilidad fue llevar la operación de 4 servicios en Render a un modelo unificado con `slidehub-monolith`.”

### 0:35 – 1:15
“El cambio principal se ve en `render.yaml`: pasamos de un despliegue distribuido a un servicio único, con health check central y configuración consolidada.”

### 1:15 – 1:45
“También se adaptó `DEPLOYMENT.md` y el `Dockerfile` del monolito, y se alineó el smoke E2E para validar el nuevo camino operativo.”

### 1:45 – 2:00
“Conclusión: menos costo, menos fricción de operación y una ruta más clara para mantener el sistema estable.”

## 5. Guía de estudio rápida (20–30 min)
1. Revisa `render.yaml` y explica la diferencia antes/ahora.
2. Estudia cómo se valida salud del servicio (`/actuator/health`).
3. Memoriza el argumento económico: menos instancias, menos complejidad operativa.
4. Ten clara la diferencia entre “build loop” y “restart por healthcheck”.

## 6. Preguntas que te pueden hacer
- **¿Por qué este cambio ahorra dinero?**  
  Porque se reduce el número de instancias y la operación multi-servicio.

- **¿Se perdió escalabilidad por mover a monolito?**  
  Para la etapa actual del proyecto, el equilibrio costo-beneficio es mejor con monolito modular; la separación lógica interna se mantiene.

## 7. Cierre de equipo (para sustentar entre los 5)
Cierra con esta idea: “Entre los 5 mostramos continuidad completa: entrada segura (Edwin), estado (Jerson), experiencia de usuario (Daniel), IA (David) e infraestructura (Mike), todo integrado en una arquitectura más simple y defendible.”
