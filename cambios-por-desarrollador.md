# Cambios por desarrollador — Migración de microservicios a monolito

**Proyecto:** SlideHub  
**Fecha:** 2026-04-04  
**Contexto:** consolidación desde arquitectura distribuida (4 microservicios) a arquitectura monolítica modular (`slidehub-monolith`).

---

## 1. Responsables acordados

- **gateway-service:** Edwin Mora
- **state-service:** Jerson Molina
- **ui-service:** Daniel Guacheta
- **ai-service:** Davin Pino
- **infraestructura:** Mike Rodriguez

---

## 2. Vista general del cambio (de dónde → a dónde)

## 2.1 Nivel arquitectura

- **Antes (microservicios):**
  - `gateway-service` (8080)
  - `state-service` (8081)
  - `ui-service` (8082)
  - `ai-service` (8083)
  - Comunicación interna HTTP entre servicios.

- **Ahora (monolito modular):**
  - `slidehub-monolith` (8080)
  - Módulos internos por paquete:
    - `com.brixo.slidehub.state`
    - `com.brixo.slidehub.ui`
    - `com.brixo.slidehub.ai`
    - `com.brixo.slidehub.monolith.ratelimit`

## 2.2 Nivel despliegue

- **Antes:** 4 Web Services en Render.
- **Ahora:** 1 Web Service (`slidehub-monolith`) en Render.

---

## 3. Detalle por desarrollador

## 3.1 Edwin Mora — `gateway-service`

## 3.1.1 Estado antes

- Responsable del gateway de entrada:
  - enrutamiento por path a cada microservicio,
  - punto central de entrada,
  - rate limiting para endpoints sensibles.
- Dependencia operativa de reglas de proxy/rutas y comunicación entre puertos internos.

## 3.1.2 De dónde → a dónde

- **Origen (legacy):** `gateway-service/**`
- **Destino (monolito):**
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/SlideHubMonolithApplication.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitFilter.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitService.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitProperties.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitDecision.java`

## 3.1.3 Qué cambió exactamente

- Se elimina la función de **proxy interno** como requisito runtime.
- Se conserva y porta la lógica de **rate limiting** al monolito.
- El “enrutamiento entre servicios” deja de ser necesario porque la invocación es in-process.

## 3.1.4 Qué se creó

- Capa de rate-limit dentro de `slidehub-monolith` (filtro + servicio + properties + decisión).

## 3.1.5 Qué dejó de existir (operativamente)

- Dependencia del gateway como salto obligatorio para llegar de UI→State/AI.
- Gestión diaria de reglas de proxy entre servicios para operación core.

## 3.1.6 Tipo de cambio

- **Cambio completo del rol técnico operativo.**

## 3.1.7 Símil (cambio completo)

- **Antes:** Edwin administraba una central de tráfico (portería + desvíos entre edificios).  
- **Ahora:** administra reglas de seguridad de entrada en un único edificio, sin necesidad de redirigir entre edificios.

---

## 3.2 Jerson Molina — `state-service`

## 3.2.1 Estado antes

- Responsable del estado de presentación:
  - slide actual,
  - modo demo,
  - registro de dispositivos,
  - eventos hápticos,
  - contratos `/api/slide`, `/api/demo`, `/api/haptics/**`.

## 3.2.2 De dónde → a dónde

- **Origen (legacy):** `state-service/src/main/java/com/brixo/slidehub/state/**`
- **Destino (monolito):** `slidehub-monolith/src/main/java/com/brixo/slidehub/state/**`

### Componentes visibles en destino

- Controllers:
  - `SlideController.java`
  - `DemoController.java`
  - `DeviceController.java`
  - `HapticController.java`
- Services:
  - `SlideStateService.java`
  - `DemoStateService.java`
  - `DeviceRegistryService.java`
  - `HapticEventService.java`
- Modelos:
  - `SlideStateResponse.java`, `DemoState.java`, `Device.java`, `HapticEvent.java`, requests asociados.

## 3.2.3 Qué cambió exactamente

- Se mantiene la lógica de dominio casi intacta.
- Cambia el contexto de ejecución: de proceso independiente a módulo interno del monolito.
- Se conserva contrato funcional para clientes.
- Ajustes de compatibilidad de rutas de slides para contexto monolito (incluyendo paths del módulo monolítico).

## 3.2.4 Qué se creó

- Réplica funcional del dominio state dentro de `slidehub-monolith`.

## 3.2.5 Qué dejó de existir (operativamente)

- Servicio state desplegado/operado como instancia independiente obligatoria.
- Dependencia de URL interna state para consumo desde UI.

## 3.2.6 Tipo de cambio

- **Cambio parcial/estructural (mismo dominio, nuevo contenedor de ejecución).**

## 3.2.7 Cómo funciona ahora

- Funciona como módulo interno `state` dentro del monolito y expone los mismos endpoints core bajo el mismo proceso.

---

## 3.3 Daniel Guacheta — `ui-service`

## 3.3.1 Estado antes

- Responsable de:
  - vistas Thymeleaf,
  - autenticación local + OAuth2,
  - controllers de presentación y meeting,
  - integración con state/ai por HTTP (`WebClient`) en varios puentes.

## 3.3.2 De dónde → a dónde

- **Origen (legacy):** `ui-service/src/main/java/com/brixo/slidehub/ui/**`
- **Destino (monolito):** `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/**`
- **Recursos destino:** `slidehub-monolith/src/main/resources/templates/**`, `static/**`, `db/migration/**`

## 3.3.3 Qué cambió exactamente

- Se mantiene el dominio UI funcional.
- Se retira dependencia de llamadas HTTP internas para funcionalidades in-process.
- Puentes internos refactorizados para invocación directa:
  - `NotesBridgeService`
  - `AssistBridgeService`
  - `HapticBridgeService`
- Se alinea documentación/vistas técnicas al modelo monolítico.

## 3.3.4 Qué se creó

- Orquestación interna directa entre módulos en el mismo runtime.
- Ajustes de configuración base URL para generación consistente de links/callbacks.

## 3.3.5 Qué dejó de existir (operativamente)

- Dependencia obligatoria de `STATE_SERVICE_URL` / `AI_SERVICE_URL` para el flujo interno principal.
- Fricción por fallas de red internas entre UI y otros servicios del mismo sistema.

## 3.3.6 Tipo de cambio

- **Cambio parcial importante (misma capa funcional, integración interna simplificada).**

## 3.3.7 Cómo funciona ahora

- UI mantiene rutas y comportamiento, pero coordina módulos `state` y `ai` dentro del mismo proceso.

---

## 3.4 Davin Pino — `ai-service`

## 3.4.1 Estado antes

- Responsable de:
  - notas IA (Gemini/Groq),
  - análisis de repos,
  - deploy tutor,
  - asistencia por audio,
  - persistencia MongoDB (`presenter_notes`, `repo_analysis`, `deployment_guides`).

## 3.4.2 De dónde → a dónde

- **Origen (legacy):** `ai-service/src/main/java/com/brixo/slidehub/ai/**`
- **Destino (monolito):** `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/**`

### Componentes visibles en destino

- Controllers:
  - `NotesController.java`
  - `RepoAnalysisController.java`
  - `DeployTutorController.java`
  - `AssistController.java`
- Services:
  - `GeminiService.java`
  - `GroqService.java`
  - `NotesService.java`
  - `RepoAnalysisService.java`
  - `DeploymentService.java`
  - `AssistService.java`
  - `GitHubRepoContextService.java`
- Repositorios Mongo:
  - `PresenterNoteRepository.java`
  - `RepoAnalysisRepository.java`
  - `DeploymentGuideRepository.java`

## 3.4.3 Qué cambió exactamente

- La lógica de IA y sus contratos se preservan.
- Se elimina la frontera de red interna con UI para varios flujos.
- Se mantiene integración externa por HTTP y MongoDB.

## 3.4.4 Qué se creó

- Port completo del dominio AI al módulo interno del monolito.

## 3.4.5 Qué dejó de existir (operativamente)

- Instancia AI independiente como requisito para operar funcionalidades IA dentro de SlideHub.
- Ruta de dependencia intra-plataforma UI→AI por red interna para operaciones locales del sistema.

## 3.4.6 Tipo de cambio

- **Cambio parcial/estructural (misma lógica de negocio IA, nuevo modo de ejecución).**

## 3.4.7 Cómo funciona ahora

- El módulo AI vive en el mismo proceso que UI/state y conserva su integración externa con proveedores IA y Mongo.

---

## 3.5 Mike Rodriguez — Infraestructura

## 3.5.1 Estado antes

- Responsable de infraestructura distribuida:
  - 4 servicios en Render,
  - health checks por servicio,
  - coordinación de variables de entorno cruzadas,
  - troubleshooting de networking interno y despliegues parciales.

## 3.5.2 De dónde → a dónde

- **Origen (legacy):** blueprint multi-servicio (`render.yaml` antiguo de 4 servicios) + runbooks de microservicios.
- **Destino (actual):**
  - `render.yaml` monolítico (1 servicio `slidehub-monolith`)
  - `DEPLOYMENT.md` monolito-first
  - `README.md` alineado a monolito modular

## 3.5.3 Qué cambió exactamente

- Se unifica despliegue en una sola unidad.
- Se simplifica health check a un flujo principal de servicio único.
- Disminuye acoplamiento de despliegues entre componentes.

## 3.5.4 Qué se creó

- Dockerfile específico del monolito (`slidehub-monolith/Dockerfile`).
- Blueprint de Render unificado para un solo servicio.
- Guía de despliegue consolidada con variables centralizadas.

## 3.5.5 Qué dejó de existir (operativamente)

- Operación diaria de 4 pipelines/servicios para correr SlideHub.
- Problemas recurrentes de proxy interno, puertos y ruteo entre servicios como requisito base.

## 3.5.6 Tipo de cambio

- **Cambio completo del modelo operativo.**

## 3.5.7 Símil (cambio completo)

- **Antes:** Mike operaba un condominio de 4 edificios con portería central y logística entre torres.  
- **Ahora:** opera un campus de edificio único con áreas internas; menos coordinación externa y menor costo de mantenimiento.

---

## 4. Matriz resumida de “cambio completo” vs “cambio parcial”

| Responsable | Componente original | Nivel de cambio | Razón |
|---|---|---|---|
| Edwin Mora | gateway-service | Completo | Se elimina función proxy interna y se conserva rate-limit como capacidad interna del monolito |
| Jerson Molina | state-service | Parcial estructural | Se conserva dominio state, cambia contenedor de ejecución |
| Daniel Guacheta | ui-service | Parcial importante | UI se conserva y se simplifica integración interna (sin HTTP interno) |
| Davin Pino | ai-service | Parcial estructural | IA conserva lógica/contratos, cambia modo de despliegue/ejecución |
| Mike Rodriguez | Infraestructura | Completo | Se pasa de operación distribuida a despliegue único |

---

## 5. Qué no desapareció (aclaración importante)

- Las carpetas legacy (`gateway-service`, `state-service`, `ui-service`, `ai-service`) **siguen en el repositorio** como referencia histórica.
- Lo que cambió fue el **camino operativo recomendado y principal**: `slidehub-monolith`.

---

## 6. Resultado final por desarrollador (en una línea)

- **Edwin:** de enrutar entre servicios a proteger entrada en monolito con rate-limit.
- **Jerson:** de servicio state independiente a módulo state interno equivalente.
- **Daniel:** de UI conectada por red interna a UI orquestando módulos in-process.
- **Davin:** de AI separada en servicio a AI integrada como módulo del mismo runtime.
- **Mike:** de gestionar 4 despliegues coordinados a operar un único despliegue consolidado.
