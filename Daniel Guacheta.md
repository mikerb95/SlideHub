# Daniel Guacheta
## Guía de exposición (2 minutos) — Capa UI y experiencia del usuario

## 1. Rol en el proyecto
Responsable de `ui-service`: vistas, autenticación, flujo de presentaciones, reuniones y orquestación con state/ai.

## 2. Qué cambió en tu parte (de microservicio a monolito)

### Antes
- UI corría en un servicio propio.
- Varios flujos dependían de llamadas HTTP internas a state/ai.
- Mayor complejidad por configuración de URLs internas.

### Ahora
- UI se integra en `slidehub-service` como módulo interno (`com.brixo.slidehub.ui`).
- Se conserva comportamiento funcional.
- Se simplifica la orquestación al reemplazar llamadas internas de red por invocaciones directas.

## 3. Archivos que debes dominar para sustentar
### Configuración
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/config/SecurityConfig.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/config/DatabaseConfig.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/config/S3Config.java`

### Controladores clave
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/AuthController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationViewController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationImportController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/PresentationNotesController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/MeetingController.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/controller/StatusController.java`

### Servicios clave
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/PresentationService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/MeetingService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/GoogleDriveService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/QuickLinkService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/StatusChecksService.java`

### Puentes migrados a in-process
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/NotesBridgeService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/AssistBridgeService.java`
- `slidehub-service/src/main/java/com/brixo/slidehub/ui/service/HapticBridgeService.java`

### Recursos
- `slidehub-service/src/main/resources/templates/**`
- `slidehub-service/src/main/resources/static/**`
- `slidehub-service/src/main/resources/db/migration/**`

## 4. Guion exacto para 2 minutos

### 0:00 – 0:35
“Mi responsabilidad era `ui-service`: interfaz, auth y flujos de presentación. Antes dependíamos de llamadas HTTP internas a otros servicios para varias acciones.”

### 0:35 – 1:15
“Después de migrar, UI vive en el monolito y conserva todas sus vistas y rutas. Lo más importante es que simplificamos la orquestación interna.”

### 1:15 – 1:45
“Los puentes `NotesBridgeService`, `AssistBridgeService` y `HapticBridgeService` pasaron de red interna a llamadas directas en el mismo proceso.”

### 1:45 – 2:00
“Resultado: misma experiencia para el usuario, menos complejidad técnica de operación.”

## 5. Guía de estudio rápida (20–30 min)
1. Prepara un mapa de 6 rutas UI críticas y su controlador.
2. Explica en 1 frase la diferencia entre “mismo comportamiento” y “menor complejidad interna”.
3. Memoriza 3 ejemplos de puentes que ya no dependen de HTTP interno.
4. Ten claro cómo se conserva seguridad (auth local + OAuth2).

## 6. Preguntas que te pueden hacer
- **¿El usuario final notó un cambio fuerte?**  
  En funcionalidad, no; en operación y estabilidad interna, sí hubo simplificación.

- **¿Qué parte fue la más importante en UI?**  
  Eliminar dependencia de red interna en flujos críticos sin perder comportamiento.

## 7. Conexión con el siguiente expositor
 Cierra diciendo: “A continuación, David presenta cómo el módulo de IA mantuvo sus capacidades al integrarse al monolito.”
