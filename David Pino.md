# David Pino
## Guía de exposición (2 minutos) — Módulo de IA

## 1. Rol en el proyecto
Responsable de `ai-service`: notas IA, análisis de repositorio, deploy tutor y asistencia por audio.

## 2. Qué cambió en tu parte (de microservicio a monolito)

### Antes
- IA estaba en un servicio independiente.
- Se comunicaba por HTTP con el resto del sistema.
- Persistía datos en MongoDB.

### Ahora
- IA se integra como módulo interno `com.brixo.slidehub.ai` dentro de `slidehub-monolith`.
- Se conserva lógica de negocio y contratos funcionales.
- Continúan integraciones externas (Gemini, Groq, MongoDB), pero sin separación de proceso para la capa interna de la plataforma.

## 3. Archivos que debes dominar para sustentar
### Controladores
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/NotesController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/RepoAnalysisController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/DeployTutorController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/controller/AssistController.java`

### Servicios
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GeminiService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GroqService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/NotesService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/RepoAnalysisService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/DeploymentService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/AssistService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/service/GitHubRepoContextService.java`

### Repositorios y modelos
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/PresenterNoteRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/RepoAnalysisRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/repository/DeploymentGuideRepository.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/PresenterNote.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/RepoAnalysis.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/ai/model/DeploymentGuide.java`

### Referencias
- `docs/MIGRACION-MONOLITO-FASE-1.md`
- `docs/MIGRACION-MONOLITO-FASE-2.md`

## 4. Guion exacto para 2 minutos

### 0:00 – 0:35
“Mi responsabilidad era `ai-service`. Ahí viven notas IA, análisis de repositorio, deploy tutor y asistencia por audio.”

### 0:35 – 1:15
“Con la migración, esta capacidad no se eliminó: se integró en el monolito como módulo `ai`, manteniendo sus endpoints y comportamiento funcional.”

### 1:15 – 1:45
“Servicios como `GeminiService`, `GroqService` y `NotesService` se conservan. También repositorios Mongo como `PresenterNoteRepository` y `RepoAnalysisRepository`.”

### 1:45 – 2:00
“En resumen: la IA sigue igual de potente, pero ahora está mejor integrada y con menos complejidad operativa interna.”

## 5. Guía de estudio rápida (20–30 min)
1. Repasa el flujo completo: entrada → contexto → generación → persistencia.
2. Ten 3 ejemplos concretos de endpoints IA y su uso.
3. Practica explicar que “cambió el despliegue, no el valor funcional”.
4. Relaciona IA con UI para mostrar integración del producto completo.

## 6. Preguntas que te pueden hacer
- **¿Perdieron integración con Gemini/Groq al migrar?**  
  No. Esas integraciones externas se mantienen.

- **¿Qué sí cambió?**  
  La IA ya no corre como proceso independiente dentro de la plataforma; corre como módulo interno del monolito.

## 7. Conexión con el siguiente expositor
Cierra diciendo: “Finalmente, Mike explica cómo toda esta integración se tradujo en una operación de infraestructura más simple y económica.”
