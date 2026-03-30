# Visión general del proyecto (Mike Rodriguez)

> Resumen conciso ligado a la web en vivo: qué hace la aplicación y dónde buscar cada pieza en el código.

SlideHub — ¿qué es?

- Aplicación de presentaciones sincronizadas multi-pantalla compuesta por 4 microservicios: `gateway-service`, `state-service`, `ui-service`, `ai-service`.
- La web ofrece vistas públicas (`/slides`, `/remote`, `/demo`) y paneles para presentadores (`/presenter`, `/main-panel`) además de APIs (`/api/**`) consumidas por los clientes.

Cómo se mapea la funcionalidad a código (rápido):

- Página de proyección de diapositivas `/slides`:
  - Vista: plantilla Thymeleaf en `ui-service/src/main/resources/templates/slides.html`.
  - Lógica de polling: JS en `ui-service` que consulta `GET /api/slide` (gateway).
  - Controlador backend (API): `com.brixo.slidehub.state.controller.SlideController` en `state-service` — devuelve `{ slide, totalSlides }`.

- Control remoto `/remote`:
  - Vista: `ui-service` template `remote.html`.
  - Acciones: envía `POST /api/slide` o rutas específicas al `gateway` que enruta a `state-service`.
  - Validaciones: manejo de límites y protección en `state-service`.

- Panel del presentador `/presenter` y `main-panel`:
  - Vistas: `ui-service` templates `presenter.html` y `main-panel.html`.
  - Backend: `PresentationViewController` en `ui-service` usa `WebClient` para leer estado de `state-service` y notas de `ai-service`.
  - Notas IA: llamadas a `ai-service` (`/api/ai/notes/*`) que entrega `PresenterNote` desde MongoDB.

- Subida y gestión de presentaciones (slides):
  - UI: formularios y endpoints en `ui-service` que reciben archivos.
  - Servicio de uploads: `SlideUploadService` en `ui-service` usando AWS SDK v2 para S3; variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`, `AWS_REGION`.

- Notas generadas por IA (`ai-service`):
  - Endpoints: `POST /api/ai/notes/generate`, `GET /api/ai/notes/{presentationId}`, `DELETE /api/ai/notes/{presentationId}`.
  - Controlador: `com.brixo.slidehub.ai.controller.NotesController` en `ai-service`.
  - Persistencia: `PresenterNoteRepository` (MongoDB). Env: `MONGODB_URI`.
  - Integraciones externas: Gemini/Groq llamadas vía `WebClient` (configuradas por variables `GEMINI_API_KEY`, `GROQ_API_KEY`).

- Gateway y enrutamiento (`gateway-service`):
  - Config: `RoutesConfig` o equivalente en `gateway-service` define rutas que rutean `/api/ai/**` → `ai-service`, `/api/**` → `state-service` y rutas UI → `ui-service`.
  - Nota crítica: el orden de las rutas importa; comprobar `gateway-service` al depurar enrutamientos.

- Estado transitorio y Redis (`state-service`):
  - Uso: `state-service` mantiene `current_slide` y `demo_state` en Redis.
  - Config vars: `REDIS_HOST`, `REDIS_PORT`.

Operación en vivo — qué ocurre al usar la web:

1. Un proyector abre `/slides`; la página hace polling a `GET /api/slide` a través del gateway. `gateway-service` enruta a `state-service`, que lee Redis y responde con el slide actual y `totalSlides` (calculado por escaneo de `static/slides/` o cache).
2. El presentador en `/presenter` avanza slide desde su UI → `ui-service` hace POST al gateway → `gateway` reenvía a `state-service` → `state-service` actualiza Redis. El polling en `/slides` refleja el cambio.
3. Si el presentador solicita notas IA, `ui-service` pide a `ai-service` (`/api/ai/notes/generate`), que consulta Gemini/Groq (o usa mocks en staging), guarda nota en MongoDB y devuelve el contenido para la vista.
4. Cuando se suben slides, `ui-service` envía el binario al `SlideUploadService` que guarda en S3 y devuelve la URL pública al frontend; la vista actualiza el catálogo de la presentación.

Puntos operativos y variables importantes (rápido):

- `STATE_SERVICE_URL`, `AI_SERVICE_URL` — configuradas en el gateway/UI para llamadas internas.
- `REDIS_HOST`, `REDIS_PORT` — estado efímero y sincronización.
- `MONGODB_URI` — persistencia de notas IA.
- `AWS_*` (S3) — uploads y assets públicos.
- `DATABASE_URL` — Postgres (usuarios, presentaciones) si aplica en `ui-service`.

Dónde mirar cuando algo falla (guía rápida de debugging):

- Problemas de polling o slides que no cambian: revisar `state-service` logs y conexión a Redis; archivo: `state-service/src/main/java/.../SlideController`.
- Errores de rutas 404/500 entre APIs: revisar `gateway-service` `RoutesConfig` y el orden de prefijos (`/api/ai/**` antes de `/api/**`).
- Fallos al generar notas IA: comprobar `ai-service` logs y `MONGODB_URI`; en staging, usar los mocks para reproducir.
- Uploads fallidos o 403: revisar permisos del bucket S3 y las credenciales en Render; `ui-service` `SlideUploadService` es el entrypoint.

Breve recomendación operativa

- Mantener claves en variables de entorno y nunca en el código.
- Para pruebas en local usar las versiones mock de Gemini/Groq y un S3 local (o bucket de desarrollo).
- Documentar cualquier cambio en los endpoints (APIs) en `docs/` y actualizar este archivo si cambian las rutas.

— Mike Rodriguez, Infra (marzo 2026)
