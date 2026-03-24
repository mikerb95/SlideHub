# SlideHub

Sistema de control de diapositivas en tiempo real con múltiples pantallas sincronizadas, control remoto desde el teléfono y notas del presentador generadas automáticamente con IA.

Empezó como un módulo PHP heredado y lo reescribimos completamente desde cero en Java usando arquitectura de microservicios. El objetivo principal era apreder Spring Boot, microservicios y las integraciones con APIs de IA — y de paso tener una herramienta que realmente fuera útil para presentar proyectos.

---

## ¿Qué hace?

La idea básica es esta: tienes una presentación de slides PNG y varias pantallas conectadas (proyector, laptop, teléfono) que deben estar sincronizadas. En lugar de cables HDMI o compartir pantalla por Zoom, cada dispositivo hace polling a la API y muestra el slide activo. Cambias el slide en el remote y todas las pantallas actualizan solas.

Encima de eso fui añadiendo cosas:

- **Control remoto táctil** — pantalla de smartphone con swipe y vibración háptica para avanzar/retroceder slides
- **QR join de equipo** — el organizador crea una sesión y el equipo entra con token y nombre de participante
- **Responsables por slide** — cada diapositiva puede tener un responsable y el sistema lo confirma con feedback háptico
- **Botón de ayuda** — manda vibración triple al resto del equipo en la sesión
- **Push-to-talk con IA** — mantén pulsado el micrófono, dicta una pregunta y recibe una respuesta con contexto del repo
- **Quick slides** — genera una slide nueva usando los colores dominantes de la presentación activa
- **Modo demo** — la pantalla de proyector puede alternar entre slides e iframes (para mostrar una app en vivo sin salir de la presentación)
- **Quick Links con returnSlide** — defines links rápidos a demos; cuando cierras el iframe vuelve exactamente al slide donde estabas
- **Notas del presentador con IA** — Gemini Vision analiza visualmente cada PNG, Gemini lee el repo de GitHub para extraer contexto, y Groq genera notas estructuradas con puntos clave, tiempo sugerido y tags de demo
- **Importar slides desde Google Drive** — conecta tu carpeta de Drive y descarga los PNGs directo a S3
- **Deploy Tutor** — pega la URL de un repo, le das la plataforma objetivo (Render, Vercel, Netlify) y genera un Dockerfile optimizado + guía de despliegue paso a paso

---

## Arquitectura

El sistema corre como 4 microservicios independientes, cada uno con su propia responsabilidad y su propia base de datos:

```
                          ┌─────────────────────┐
                          │   gateway-service   │  :8080
                          │   (API Gateway +    │
                          │    Config Server)   │
                          └──────────┬──────────┘
                  ┌──────────────────┼──────────────────┐
                  ▼                  ▼                   ▼
        ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
        │ state-service│   │  ui-service  │   │  ai-service  │
        │   :8081      │   │    :8082     │   │    :8083     │
        │              │   │              │   │              │
        │  Redis       │   │  PostgreSQL  │   │  MongoDB     │
        │  (Upstash)   │   │  (Aiven)     │   │  (Atlas)     │
        └──────────────┘   └──────────────┘   └──────────────┘
```

| Servicio | Responsabilidad |
|----------|-----------------|
| `gateway-service` | Punto de entrada único, enrutamiento, Config Server |
| `state-service` | Estado de la presentación en Redis (qué slide está activo, modo demo) |
| `ui-service` | Vistas Thymeleaf, autenticación, importar slides, subir a S3 |
| `ai-service` | Notas del presentador, análisis de repos, generación de Dockerfiles |

Esta separación probablemente es overkill para un proyecto personal, pero es exactamente para aprender a manejar multi-módulo Maven, comunicación inter-servicio vía WebClient y configuración de PostgreSQL + Redis + MongoDB al mismo tiempo.

---

## Vistas

| Ruta | Acceso | Para quién |
|------|--------|------------|
| `/slides` | Público | Proyector / pantalla de la audiencia |
| `/remote` | Público | Control desde el teléfono |
| `/demo` | Público | Pantalla que alterna slides e iframes |
| `/showcase` | Público | Landing page del proyecto |
| `/presenter` | PRESENTER | Laptop del presentador — notas + timer |
| `/main-panel` | PRESENTER | Panel maestro para tablet — thumbnails, navegación, quick links |
| `/deploy-tutor` | PRESENTER | Generador de Dockerfiles y guías de despliegue |
| `/presentations` | PRESENTER | Gestión de presentaciones guardadas |
| `/presentations/import` | PRESENTER | Importar desde Google Drive |
| `/auth/profile` | PRESENTER | Perfil y cuentas OAuth vinculadas |
| `/auth/login` | Público | Login local o con GitHub/Google |

---

## Stack

**Backend:**
- Java 21 + Spring Boot 4.0.3 + Spring Cloud 2025.1.0
- Spring Security con BCrypt + OAuth2 (GitHub y Google coexistiendo)
- Spring Data JPA → PostgreSQL (Aiven) con migraciones Flyway
- Spring Data Redis → Upstash para estado efímero
- Spring Data MongoDB → MongoDB Atlas para notas IA y guías de deploy
- WebClient (WebFlux) para todas las integraciones externas — Gemini, Groq, Google Drive, Resend
- AWS SDK v2 para S3 (única excepción al patrón "todo por HTTP puro" porque S3 necesita firma SigV4)

**Frontend:**
- Thymeleaf 3 para templating server-side
- Bootstrap 5.3 + Font Awesome 6.5 desde CDN
- Vanilla JS con `fetch()` para polling — sin jQuery, sin frameworks

**IA:**
- Gemini Vision — análisis visual de slides PNG
- Gemini API — extracción de contexto desde repos de GitHub
- Groq (Llama 3) — generación de notas estructuradas y Dockerfiles

---

## Correr localmente

### Prerequisitos

- Java 21
- Docker (para Redis y MongoDB locales)
- Maven (o usa el wrapper `./mvnw`)

### Variables de entorno

Copia `.env.example` a `.env` y rellena los valores:

```bash
cp .env.example .env
```

Las únicas variables que se necesitan sí o sí para el flujo básico (slides + remote):

```env
SPRING_PROFILES_ACTIVE=dev
# Las demás tienen defaults en application.properties
```

Las funciones de IA y OAuth necesitan sus respectivas API keys.

### Levantar servicios de apoyo con Docker

```bash
docker run -d -p 6379:6379 redis:alpine
docker run -d -p 27017:27017 mongo:6
```

PostgreSQL local (si no quieres usar Aiven):
```bash
docker run -d -p 5432:5432 -e POSTGRES_DB=slidehub -e POSTGRES_USER=slidehub -e POSTGRES_PASSWORD=slidehub postgres:14
```

### Compilar y ejecutar

```bash
# Compilar todo
./mvnw clean compile

# Ejecutar cada servicio en su propia terminal
./mvnw spring-boot:run -pl state-service
./mvnw spring-boot:run -pl ui-service
./mvnw spring-boot:run -pl ai-service
./mvnw spring-boot:run -pl gateway-service
```

Abre `http://localhost:8080/slides` para ver la pantalla del proyector.

### Compilar un módulo específico

```bash
./mvnw clean compile -pl state-service -am
```

### Ejecutar tests

```bash
./mvnw test -pl ai-service
./mvnw test -pl state-service
```

---

## API de Estado y Reuniones (state-service + ui-service)

La API principal que usan todos los dispositivos cliente:

```
GET  /api/slide              → { "slide": 3, "totalSlides": 11 }
POST /api/slide              → { "slide": 4 }
GET  /api/demo               → { "mode": "slides", "slide": 3, "returnSlide": null }
POST /api/demo               → { "mode": "url", "url": "/demo-path", "returnSlide": 3 }

GET  /api/presentations/{id}/meeting/participants      → lista de participantes
POST /api/presentations/{id}/meeting/participants     → alta de participante
GET  /api/presentations/{id}/meeting/assignments       → asignaciones slide-responsable
POST /api/presentations/{id}/meeting/assignments      → asignar responsable a slide
POST /api/presentations/{id}/meeting/session/start    → crea sesión con joinToken
GET  /api/presentations/{id}/meeting/join-options     → opciones de ingreso por QR
POST /api/presentations/{id}/meeting/join             → entra a la sesión y devuelve participantToken
GET  /api/presentations/{id}/meeting/assignment-check → vibración si ese slide es responsable
POST /api/presentations/{id}/meeting/help             → ayuda con vibración triple al equipo
POST /api/presentations/{id}/meeting/assist/audio     → transcribe audio y devuelve una respuesta IA
```

```
GET  /api/devices            → lista de dispositivos registrados (solo ADMIN)
GET  /api/devices/token/{t}  → buscar dispositivo por token (solo ADMIN)

GET  /api/haptics/events/next?participantToken=...    → siguiente evento háptico para un participante
POST /api/haptics/events/publish                     → publica un evento háptico
```

El polling de los clientes va **siempre al gateway en el puerto 8080**, no directamente al state-service ni a los controladores internos de ui-service.

## API de IA (ai-service)

```
POST /api/ai/notes/generate              → genera nota para un slide
GET  /api/ai/notes/{presentationId}      → todas las notas de una presentación
GET  /api/ai/notes/{presentationId}/{n}  → nota de un slide (204 si no existe)
DELETE /api/ai/notes/{presentationId}    → borra todas las notas

POST /api/ai/deploy/analyze              → detecta lenguaje, framework, puertos
POST /api/ai/deploy/dockerfile           → genera Dockerfile optimizado
POST /api/ai/deploy/guide                → genera guía de despliegue (con cache)
POST /api/ai/deploy/guide/refresh        → regenera descartando el cache
POST /api/ai/assist/audio                → transcribe audio y devuelve una respuesta asistida

GET  /api/ai/notes/health                → health check del ai-service
```

---

## Estructura del monorepo

```
SlideHub/
├── pom.xml                   ← Parent POM (sin código, solo agrega módulos)
├── render.yaml               ← Blueprint de Render para despliegue automático
├── gateway-service/          ← Puerto 8080 — Spring Cloud Gateway
├── state-service/            ← Puerto 8081 — estado en Redis
├── ui-service/               ← Puerto 8082 — Thymeleaf + Security + JPA
│   └── src/main/resources/
│       ├── templates/        ← Vistas HTML
│       ├── static/slides/    ← Slides PNG (Slide_1.PNG, Slide_2.PNG, ...)
│       └── db/migration/     ← Scripts Flyway
└── ai-service/               ← Puerto 8083 — Gemini + Groq + MongoDB
```

Los slides PNG van en `ui-service/src/main/resources/static/slides/` con el nombre `Slide_N.PNG` y el sistema los detecta automáticamente.
Las vistas también consumen el catálogo por presentación cuando existe `presentationId`; el recurso estático queda como fallback.

---

## Despliegue en Render

El proyecto incluye un `render.yaml` con los 4 servicios definidos. Para desplegarlo:

1. Sube el repo a GitHub
2. En Render → Blueprints → New from Blueprint → pega la URL del repo
3. Render detecta el `render.yaml` automáticamente
4. Rellena las variables de entorno que te pide
5. Deploy

Cada servicio tiene su propio Dockerfile multi-stage (builder con JDK 21 Alpine + runtime con solo el JRE). El resultado son imágenes de ~150-200 MB por servicio.

Más detalles en [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Convenciones del proyecto

Algunos patrones que se siguen consistentemente:

- Records Java para DTOs inmutables, `@Document` / `@Entity` para entidades persistidas
- Inyección únicamente por constructor — sin `@Autowired` en campos
- `ResponseEntity<T>` en todos los endpoints de `state-service` y `ai-service`
- Sin Lombok ni MapStruct — código explícito aunque sea más largo
- Gemini, Groq y Google Drive se integran **solo por HTTP** con `WebClient`, sin SDKs de terceros
- SLF4J para logs — cero `System.out.println()`
- Variables de entorno para todo lo que sea secreto — ninguna API key en el código

---

## Documentación adicional

- [AGENTS.md](AGENTS.md) — especificaciones detalladas de arquitectura, historias de usuario y convenciones para trabajar con IA en el repositorio
- [DEPLOYMENT.md](DEPLOYMENT.md) — guía de despliegue paso a paso en Render
- [docs/](docs/) — análisis del módulo PHP original y las historias de usuario en CSV
