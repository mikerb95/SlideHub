# CAMBIOS POR DESARROLLADOR
## Migración de arquitectura de microservicios a monolito en SlideHub

**Asignatura (formato):** Ingeniería de Software / Arquitectura de Software  
**Proyecto:** SlideHub  
**Fecha:** 2026-04-04  
**Elaborado para:** documentación académica y trazabilidad técnica del cambio arquitectónico

---

## Resumen

Este documento presenta, de manera ordenada y con enfoque académico, cómo cambió el trabajo de cada responsable durante la migración de SlideHub desde una arquitectura de microservicios (4 servicios) hacia un monolito modular (`slidehub-monolith`).

El informe conserva el detalle completo de archivos, rutas y componentes. Sin embargo, su redacción es menos técnica y más explicativa, con lenguaje apropiado para un trabajo universitario.

Se explica para cada persona:
- cuál era su servicio antes,
- qué cambió (total o parcialmente),
- qué se creó,
- qué dejó de existir a nivel operativo,
- de qué ruta de proyecto a qué ruta migró,
- y cómo funciona ahora su área.

---

## 1. Introducción

SlideHub comenzó con cuatro microservicios independientes:
- `gateway-service`
- `state-service`
- `ui-service`
- `ai-service`

En ese modelo, cada servicio corría por separado y se comunicaba por HTTP interno. Con el tiempo, se decidió simplificar la operación y unificar el despliegue en un único proceso, manteniendo la separación lógica por módulos internos.

Por ello, el estado actual del proyecto es un monolito modular llamado `slidehub-monolith`, donde existen módulos de negocio diferenciados (`state`, `ui`, `ai`) pero dentro de una sola aplicación.

---

## 2. Objetivos del documento

1. Registrar de forma clara los cambios por responsable.
2. Explicar el paso “de dónde a dónde” por servicio.
3. Dejar evidencia de archivos creados o adaptados.
4. Diferenciar cambios completos frente a cambios parciales.
5. Facilitar lectura académica y trazabilidad para futuras revisiones.

---

## 3. Metodología utilizada

Para elaborar este informe se tomaron como base los artefactos oficiales de migración y estado del repositorio:

- `docs/MIGRACION-MONOLITO-FASE-0.md`
- `docs/MIGRACION-MONOLITO-FASE-1.md`
- `docs/MIGRACION-MONOLITO-FASE-2.md`
- `docs/MIGRACION-MONOLITO-FASE-3-DEPLOY-DOCS.md`
- `docs/MIGRACION-MONOLITO-FASE-4-SMOKE.md`
- `README.md`
- `DEPLOYMENT.md`
- `render.yaml`

Adicionalmente, se revisó la estructura real del módulo `slidehub-monolith` para confirmar la presencia efectiva de clases y componentes migrados.

---

## 4. Responsables considerados

- **gateway-service:** Edwin Mora
- **state-service:** Jerson Molina
- **ui-service:** Daniel Guacheta
- **ai-service:** Davin Pino
- **Infraestructura:** Mike Rodriguez

---

## 5. Desarrollo por responsable

## 5.1 Edwin Mora — Gateway Service

### 5.1.1 Situación antes de la migración

Edwin estaba a cargo del punto de entrada principal del sistema (`gateway-service`). Sus tareas estaban centradas en:
- enrutar peticiones al servicio correcto,
- actuar como “puerta” de entrada,
- aplicar controles de protección (rate limiting) para evitar abuso.

### 5.1.2 De dónde a dónde migró

- **Origen:** `gateway-service/**`
- **Destino funcional en monolito:**
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/SlideHubMonolithApplication.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitFilter.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitService.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitProperties.java`
  - `slidehub-monolith/src/main/java/com/brixo/slidehub/monolith/ratelimit/GatewayRateLimitDecision.java`

### 5.1.3 Qué cambió

El cambio fue **completo** en términos operativos. La parte de “proxy entre servicios” deja de ser necesaria porque los módulos están en el mismo proceso.

Lo que sí se conserva es la lógica de protección de entrada (rate limiting), ahora embebida en el monolito.

### 5.1.4 Qué se creó y qué dejó de existir

**Se creó:**
- una capa de rate limiting interna en el monolito.

**Dejó de existir (operativamente):**
- la necesidad de enrutar UI→State→AI como saltos entre servicios independientes.

### 5.1.5 Símil pedagógico

Antes, Edwin coordinaba tráfico entre varios edificios conectados. Ahora administra el control de acceso en un solo edificio con áreas internas.

---

## 5.2 Jerson Molina — State Service

### 5.2.1 Situación antes de la migración

Jerson era responsable del estado de la presentación:
- slide actual,
- modo demo,
- registro de dispositivos,
- eventos hápticos,
- endpoints de estado (`/api/slide`, `/api/demo`, `/api/haptics/**`).

### 5.2.2 De dónde a dónde migró

- **Origen:** `state-service/src/main/java/com/brixo/slidehub/state/**`
- **Destino:** `slidehub-monolith/src/main/java/com/brixo/slidehub/state/**`

### 5.2.3 Archivos principales en destino

**Controladores:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/SlideController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/DemoController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/DeviceController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/HapticController.java`

**Servicios:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/SlideStateService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/DemoStateService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/DeviceRegistryService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/HapticEventService.java`

**Modelos:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/SlideStateResponse.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/DemoState.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/Device.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/HapticEvent.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/SetSlideRequest.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/SetDemoRequest.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/RegisterDeviceRequest.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/PublishHapticRequest.java`

### 5.2.4 Qué cambió

El cambio fue **parcial (estructural)**. El negocio de state se mantiene, pero su ejecución deja de ser un servicio autónomo y pasa a ser un módulo interno.

### 5.2.5 Qué se creó y qué dejó de existir

**Se creó:**
- la versión integrada del dominio state dentro del monolito.

**Dejó de existir (operativamente):**
- state-service como despliegue independiente obligatorio.

### 5.2.6 Cómo funciona ahora

State conserva sus contratos funcionales, pero trabaja dentro de la misma aplicación que UI y AI.

---

## 5.3 Daniel Guacheta — UI Service

### 5.3.1 Situación antes de la migración

Daniel lideraba la capa visual y de acceso:
- vistas Thymeleaf,
- autenticación local y OAuth2,
- controllers de presentaciones y reuniones,
- comunicación con state/ai por HTTP interno en diversos puentes.

### 5.3.2 De dónde a dónde migró

- **Origen:** `ui-service/src/main/java/com/brixo/slidehub/ui/**`
- **Destino:** `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/**`
- **Recursos migrados al monolito:**
  - `slidehub-monolith/src/main/resources/templates/**`
  - `slidehub-monolith/src/main/resources/static/**`
  - `slidehub-monolith/src/main/resources/db/migration/**`

### 5.3.3 Archivos representativos en destino

**Configuración:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/config/SecurityConfig.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/config/DatabaseConfig.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/config/S3Config.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/config/ForwardedHostFilter.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/config/UserActivityTrackingFilter.java`

**Controladores:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/AuthController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/PresentationViewController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/PresentationImportController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/PresentationNotesController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/PresenterViewController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/MeetingController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/StatusController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/DocsController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/controller/QuickLinkController.java`

**Servicios (selección):**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/PresentationService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/MeetingService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/GoogleDriveService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/SlideUploadService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/QuickLinkService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/UserService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/StatusChecksService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/KeepAliveService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/EmailService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/AuthenticatedSessionTracker.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/UserActivityTracker.java`

**Puentes internos refactorizados a in-process:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/NotesBridgeService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/AssistBridgeService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ui/service/HapticBridgeService.java`

### 5.3.4 Qué cambió

El cambio fue **parcial importante**. La capa UI se mantiene, pero la comunicación interna deja de depender de red entre servicios y pasa a llamadas directas dentro del mismo proceso.

### 5.3.5 Qué se creó y qué dejó de existir

**Se creó:**
- orquestación interna simplificada UI↔state↔ai.

**Dejó de existir (operativamente):**
- dependencia obligatoria de URLs internas para coordinación entre componentes del propio sistema.

### 5.3.6 Cómo funciona ahora

La experiencia de usuario se conserva, pero la operación es más simple porque todo corre en un único runtime.

---

## 5.4 Davin Pino — AI Service

### 5.4.1 Situación antes de la migración

Davin lideraba la capa de inteligencia artificial:
- notas para presentador,
- análisis de repositorio,
- deploy tutor,
- asistencia por audio,
- persistencia MongoDB para resultados IA.

### 5.4.2 De dónde a dónde migró

- **Origen:** `ai-service/src/main/java/com/brixo/slidehub/ai/**`
- **Destino:** `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/**`

### 5.4.3 Archivos principales en destino

**Controladores:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/NotesController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/RepoAnalysisController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/DeployTutorController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/AssistController.java`

**Servicios:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GeminiService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GroqService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/NotesService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/RepoAnalysisService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/DeploymentService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/AssistService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GitHubRepoContextService.java`

**Repositorios y modelos:**
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/PresenterNoteRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/RepoAnalysisRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/DeploymentGuideRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/PresenterNote.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/RepoAnalysis.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/DeploymentGuide.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/NoteContent.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/GenerateNoteRequest.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/GenerateAllRequest.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/SlideReference.java`

### 5.4.4 Qué cambió

El cambio fue **parcial (estructural)**. La lógica principal de IA y sus contratos se conservan, pero su ejecución queda integrada en el monolito.

### 5.4.5 Qué se creó y qué dejó de existir

**Se creó:**
- integración de toda la capa AI en el módulo interno del monolito.

**Dejó de existir (operativamente):**
- necesidad de desplegar `ai-service` como instancia aparte para la operación normal de SlideHub.

### 5.4.6 Cómo funciona ahora

AI sigue consumiendo proveedores externos (Gemini/Groq) y MongoDB, pero ya no vive en un proceso separado del resto del sistema.

---

## 5.5 Mike Rodriguez — Infraestructura

### 5.5.1 Situación antes de la migración

Mike gestionaba una infraestructura distribuida:
- cuatro servicios independientes,
- health checks por cada servicio,
- múltiples configuraciones de entorno,
- coordinación de despliegues parciales.

### 5.5.2 De dónde a dónde migró

- **Origen:** blueprint de Render para varios servicios.
- **Destino:** despliegue único monolítico.

### 5.5.3 Archivos clave de infraestructura y despliegue

- `render.yaml` (definición unificada a `slidehub-monolith`)
- `slidehub-monolith/Dockerfile` (build y runtime del monolito)
- `DEPLOYMENT.md` (guía operativa monolito-first)
- `README.md` (arquitectura actual declarada como monolito modular)

### 5.5.4 Qué cambió

El cambio fue **completo** en el modelo operativo: de múltiples despliegues coordinados a un despliegue principal único.

### 5.5.5 Qué se creó y qué dejó de existir

**Se creó:**
- flujo de despliegue simplificado para un solo servicio.

**Dejó de existir (operativamente):**
- necesidad de operar de forma diaria cuatro pipelines/servicios como requisito de funcionamiento base.

### 5.5.6 Símil pedagógico

Antes, Mike coordinaba cuatro sedes conectadas. Ahora administra una sede central con áreas internas, con menos sobrecarga de coordinación.

---

## 6. Cuadro comparativo general

| Responsable | Componente previo | Tipo de cambio | Interpretación académica |
|---|---|---|---|
| Edwin Mora | gateway-service | Completo | El rol de enrutamiento distribuido se transforma en control de acceso interno |
| Jerson Molina | state-service | Parcial | Se preserva el dominio state, cambia la forma de despliegue |
| Daniel Guacheta | ui-service | Parcial | Se mantiene la capa de interfaz, pero se simplifica la integración interna |
| Davin Pino | ai-service | Parcial | Se conserva el negocio IA y se integra al runtime único |
| Mike Rodriguez | Infraestructura | Completo | Se sustituye operación distribuida por operación unificada |

---

## 7. Aclaración sobre elementos legacy

Las carpetas de microservicios todavía existen en el repositorio como referencia histórica:
- `gateway-service/`
- `state-service/`
- `ui-service/`
- `ai-service/`

Esto no contradice la migración. El cambio principal es **operativo y arquitectónico de ejecución**, no necesariamente la eliminación inmediata de todos los artefactos históricos.

---

## 8. Conclusiones

1. La migración no implicó pérdida del núcleo funcional de cada responsable, sino un cambio de entorno de ejecución.
2. Los cambios completos ocurrieron sobre todo en gateway e infraestructura, porque su razón de ser dependía de la distribución en múltiples servicios.
3. Los cambios parciales se dieron en state, ui y ai: se conservaron capacidades, pero se simplificó su integración.
4. La trazabilidad por archivos demuestra que la transición se hizo de manera progresiva y documentada por fases.
5. El resultado final favorece una operación más sencilla sin romper la división lógica del sistema.

---

## 9. Referencias internas del proyecto

- `docs/MIGRACION-MONOLITO-FASE-0.md`
- `docs/MIGRACION-MONOLITO-FASE-1.md`
- `docs/MIGRACION-MONOLITO-FASE-2.md`
- `docs/MIGRACION-MONOLITO-FASE-3-DEPLOY-DOCS.md`
- `docs/MIGRACION-MONOLITO-FASE-4-SMOKE.md`
- `README.md`
- `DEPLOYMENT.md`
- `render.yaml`
