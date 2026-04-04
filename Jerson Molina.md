# Jerson Molina
## Guía de exposición (2 minutos) — Estado de presentación (`state`)

## 1. Rol en el proyecto
Responsable de `state-service`: estado de slides, demo, dispositivos y eventos hápticos.

## 2. Qué cambió en tu parte (de microservicio a monolito)

### Antes
- `state-service` era un proceso separado.
- Atendía endpoints de estado y sincronización.
- Dependía de despliegue independiente.

### Ahora
- Tu dominio sigue existiendo casi igual, pero como módulo interno:
  - `com.brixo.slidehub.state`
- Se conserva funcionalidad y contratos, cambia solo el contenedor de ejecución.

## 3. Archivos que debes dominar para sustentar
### Controladores
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/SlideController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/DemoController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/DeviceController.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/controller/HapticController.java`

### Servicios
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/SlideStateService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/DemoStateService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/DeviceRegistryService.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/service/HapticEventService.java`

### Modelos
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/SlideStateResponse.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/DemoState.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/Device.java`
- `slidehub-monolith/src/main/java/com/brixo/slidehub/state/model/HapticEvent.java`

### Referencias de migración
- `docs/MIGRACION-MONOLITO-FASE-1.md`
- `docs/MIGRACION-MONOLITO-FASE-2.md`

## 4. Guion exacto para 2 minutos

### 0:00 – 0:35
“Yo lideraba `state-service`, que controla slide actual, demo, dispositivos y eventos hápticos. Era un servicio separado dentro de la arquitectura distribuida.”

### 0:35 – 1:15
“Con la migración, esa lógica no se elimina: se integra al módulo `state` dentro de `slidehub-monolith`. Los endpoints y comportamiento principal se mantienen.”

### 1:15 – 1:45
“Los componentes clave en el monolito son `SlideController`, `DemoController`, `SlideStateService` y `HapticEventService`. Es el mismo dominio, pero sin despliegue aislado.”

### 1:45 – 2:00
“Conclusión: no cambiamos el negocio de estado; cambiamos la forma de ejecutarlo para simplificar operación.”

## 5. Guía de estudio rápida (20–30 min)
1. Enumera 4 endpoints de estado y su propósito.
2. Relaciona cada endpoint con su controlador.
3. Prepara una frase para explicar por qué este cambio fue parcial, no total.
4. Repasa cómo state se conecta con UI/AI ahora (in-process).

## 6. Preguntas que te pueden hacer
- **¿Perdieron funcionalidades al migrar state?**  
  No. Se conserva la funcionalidad; se movió al módulo interno del monolito.

- **¿Qué ventaja obtuvieron?**  
  Menos dependencia de red interna y menos puntos de falla operativos.

## 7. Conexión con el siguiente expositor
Cierra diciendo: “Ahora Daniel explica cómo la capa UI se simplificó al dejar de consumir servicios internos por HTTP.”
