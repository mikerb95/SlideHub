# Migración de Microservicios a Monolito — Documento Consolidado

**Proyecto:** SlideHub  
**Fecha de consolidación:** 2026-04-04  
**Estado actual:** Monolito modular en producción (`slidehub-monolith`)

---

## 1) Resumen ejecutivo

SlideHub migró de una arquitectura de **4 microservicios** (`gateway-service`, `state-service`, `ui-service`, `ai-service`) a un **monolito modular** (`slidehub-monolith`) para simplificar operación, reducir costos de infraestructura y eliminar complejidad innecesaria de red interna (proxy/ruteo/puertos entre servicios).

La migración mantuvo la separación lógica por módulos (`ui`, `state`, `ai`) dentro de un único proceso Spring Boot, conservando el comportamiento funcional de negocio.

---

## 2) Alcance de la migración

### Arquitectura origen (microservicios)
- `gateway-service` (entrada única, ruteo)
- `state-service` (estado/polling/haptics)
- `ui-service` (vistas/auth)
- `ai-service` (Gemini/Groq/notas/deploy tutor)
- Despliegue multi-servicio en Render

### Arquitectura destino (monolito modular)
- `slidehub-monolith` (único servicio, puerto único)
- Módulos internos:
  - `ui`
  - `state`
  - `ai`
- Despliegue recomendado: 1 solo Web Service en Render (`slidehub-monolith`)

---

## 3) Qué se tuvo en cuenta durante la migración

## 3.1 Continuidad funcional
- Mantener rutas y flujos de usuario (`/slides`, `/remote`, `/presenter`, `/main-panel`, `/presentations`, `/api/**`).
- Mantener integraciones externas (Gemini, Groq, Google Drive, Resend, S3) sin alterar contratos funcionales.
- Evitar regresiones en notas IA, control remoto, demo mode, importación de slides y auth.

## 3.2 Persistencia y datos
- Preservar almacenamiento híbrido existente:
  - PostgreSQL (usuarios/presentaciones/sesiones)
  - Redis (estado efímero/haptics)
  - MongoDB (notas IA/análisis/deploy guides)
- Mantener migraciones de BD existentes (Flyway).

## 3.3 Seguridad y autenticación
- Mantener Spring Security y OAuth2 (GitHub/Google).
- Conservar reglas de acceso por rol (`PRESENTER`, `ADMIN`) y rutas públicas.

## 3.4 Operación y despliegue
- Reducir dependencia del gateway como proxy interno entre servicios.
- Evitar coordinación de múltiples health checks entre servicios internos.
- Centralizar build, deploy y observabilidad básica en una sola unidad de despliegue.

## 3.5 Compatibilidad y transición
- Conservar en repo la estructura legacy de microservicios como referencia histórica.
- Ajustar terminología técnica/UI para reflejar “monolito modular” en vez de “4 servicios”.

---

## 4) Qué se creó

## 4.1 Módulo de ejecución principal
- **`slidehub-monolith/`** como servicio principal de ejecución y despliegue.

## 4.2 Artefactos y documentación de migración
- Fases de migración documentadas:
  - `docs/MIGRACION-MONOLITO-FASE-0.md`
  - `docs/MIGRACION-MONOLITO-FASE-1.md`
  - `docs/MIGRACION-MONOLITO-FASE-2.md`
  - `docs/MIGRACION-MONOLITO-FASE-3-DEPLOY-DOCS.md`
  - `docs/MIGRACION-MONOLITO-FASE-4-SMOKE.md`
- Documentación de despliegue monolítico:
  - `DEPLOYMENT.md`
- Blueprint monolítico de Render:
  - `render.yaml` (servicio único `slidehub-monolith`)

## 4.3 Ajustes funcionales/técnicos relevantes
- Actualización de narrativa UI y técnica a monolito modular en vistas/documentación.
- Ajustes de detección de rutas de slides para contexto monolito (compatibilidad de path).
- Consolidación de la ejecución local/build en torno a `slidehub-monolith`.

---

## 5) Qué dejó de existir (o dejó de ser obligatorio)

> Nota importante: en varios casos el componente **no desaparece físicamente del repositorio**, pero **deja de ser parte del modelo operativo recomendado**.

## 5.1 Dejó de existir como requisito operativo
- Despliegue obligatorio de 4 servicios independientes para operar SlideHub.
- Necesidad de gateway como proxy interno entre servicios para el camino principal de ejecución.
- Gestión diaria de conectividad interna por URLs cruzadas entre servicios como arquitectura por defecto.
- Coordinación de múltiples pipelines de build/deploy por servicio como requisito base.

## 5.2 Dejó de existir como complejidad principal de runtime
- Orquestación de puertos internos por microservicio para el funcionamiento normal de la app.
- Dependencia de ruteo interno por path entre procesos separados para las funciones core.

## 5.3 Se conserva como legado/histórico
- Carpetas:
  - `gateway-service/`
  - `state-service/`
  - `ui-service/`
  - `ai-service/`
- Se mantienen para referencia histórica, validación comparativa y transición controlada.

---

## 6) Cambios por dominio técnico

## 6.1 Build y empaquetado
- Se mantiene monorepo Maven, pero la ruta principal de compilación/ejecución se centra en:
  - `./mvnw clean compile -pl slidehub-monolith -am`
  - `./mvnw spring-boot:run -pl slidehub-monolith`

## 6.2 Despliegue
- `render.yaml` consolidado a un solo servicio (`slidehub-monolith`) con health check único:
  - `/actuator/health`

## 6.3 Frontend/UX técnico
- Vistas y textos técnicos actualizados para evitar contradicción arquitectónica (microservicios vs monolito).

## 6.4 Integraciones externas
- Se mantienen sin cambio de paradigma:
  - HTTP `WebClient` para Gemini/Groq/Drive/Resend
  - AWS SDK v2 para S3

---

## 7) Riesgos evaluados y mitigaciones

## 7.1 Riesgos
- Mayor radio de impacto por despliegue único (si falla, afecta todo).
- Necesidad de disciplina modular interna para evitar “big ball of mud”.
- Crecimiento de tiempos de build en un único artefacto.

## 7.2 Mitigaciones aplicadas
- Mantener separación por módulos lógicos (`ui/state/ai`) y paquetes.
- Mantener documentación de fases y smoke tests de migración.
- Mantener stores desacoplados (PostgreSQL/Redis/Mongo) y prácticas de configuración por entorno.

---

## 8) Evidencia de estado final

- README declara explícitamente estado actual monolítico modular.
- `render.yaml` define despliegue de un único servicio.
- `DEPLOYMENT.md` prescribe flujo de deploy monolítico.
- Fases de migración documentadas en `docs/MIGRACION-MONOLITO-FASE-*`.

---

## 9) Justificación del cambio (sustento de negocio y operación)

La migración se realizó por razones técnicas y económicas concretas:

## 9.1 Ahorro de costos de despliegue
- Pasar de múltiples instancias a **una sola instancia** reduce costo base mensual.
- Menor consumo operativo asociado a despliegues por servicio, monitoreo y reinicios independientes.

## 9.2 Menor complejidad operativa
- Se evita operar y depurar una malla interna de servicios para casos donde no era estrictamente necesaria.
- Menos puntos de falla por red interna entre servicios.

## 9.3 Eliminación de carga del gateway como pieza obligatoria
- Se reduce la necesidad de gestionar reglas de proxy/ruteo interno para cada path entre servicios.
- Se simplifica troubleshooting de latencia/rutas porque el core vive en un proceso único.

## 9.4 Simplificación de puertos, networking y configuración
- Se evita coordinar múltiples puertos internos como condición de funcionamiento diario.
- Menos variables cruzadas de URL interna por servicio.
- Menor riesgo de errores de configuración de comunicación inter-servicios.

## 9.5 Menor fricción de CI/CD
- Pipeline principal más directo: construir y desplegar un solo artefacto.
- Menos acoplamiento entre despliegues parciales que puedan romper contrato entre servicios.

## 9.6 Mejor equilibrio costo-beneficio para el tamaño actual del producto
- Para la etapa actual de SlideHub, la complejidad de microservicios no compensa el costo operativo.
- Monolito modular mantiene orden arquitectónico interno, con costo y operación mucho más simples.

---

## 10) Resultado final

SlideHub queda operando con una arquitectura **monolítica modular**, preservando capacidades funcionales principales y reduciendo complejidad/costo de plataforma.

En términos prácticos:
- **Se gana simplicidad operativa y económica.**
- **Se pierde complejidad innecesaria de infraestructura distribuida para la escala actual.**
- **Se mantiene trazabilidad de la migración y legado técnico en el repositorio.**
