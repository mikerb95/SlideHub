# Términos Legales y Políticas de SlideHub

Este documento establece las políticas de privacidad, normas de uso, exención de responsabilidad y derechos de autor aplicables al sistema de presentaciones SlideHub.

---

## 1. Derechos de Autor (Copyright)

**Copyright &copy; 2026 Brixo / SlideHub. Todos los derechos reservados (All Rights Reserved).**

Este repositorio y su código fuente son **código cerrado** y se publican únicamente con fines de visibilidad, portafolio y referencia. 
*   **No se concede ninguna licencia** implícita ni explícita para usar, copiar, modificar, fusionar, publicar, distribuir, sublicenciar y/o vender copias del software.
*   La publicación pública de este código no equivale a una licencia de código abierto (Open Source).
*   Cualquier uso comercial, redistribución o despliegue privado requiere **autorización escrita previa** por parte del titular de los derechos.

---

## 2. Política de Privacidad

SlideHub respeta la privacidad de sus usuarios (Presentadores y Audiencia) y se compromete a proteger los datos personales.

*   **Información Recopilada:** Recopilamos direcciones de correo electrónico, nombres de usuario, tokens de autenticación OAuth2 (GitHub y Google), direcciones IP para la sesión en curso y archivos subidos (diapositivas/imágenes).
*   **Uso de la Información:** Los datos se utilizan exclusivamente para:
    *   Gestionar y autenticar el acceso (inicio de sesión local y OAuth2).
    *   Sincronizar las pantallas de los participantes mediante identificadores de sesión.
    *   Enviar correos electrónicos transaccionales o de verificación de cuentas (a través de Resend).
*   **Procesamiento de Inteligencia Artificial (IA):** 
    *   Las diapositivas, textos y repositorios de código vinculados son procesados a través de integraciones de terceros (Google Gemini, Gemini Vision y Groq API) para la generación de notas y asistentes de despliegue. 
    *   Al utilizar estas funciones, el usuario acepta que fragmentos de sus repositorios e imágenes sean enviados de manera segura a las API de estos proveedores.
*   **Almacenamiento:**
    *   **Imágenes y Diapositivas:** Amazon S3.
    *   **Datos Relacionales (Usuarios, perfiles):** Base de datos PostgreSQL administrada por Aiven.
    *   **Notas generadas (IA):** Base de datos MongoDB.
    *   **Estado en tiempo real:** Redis (volátil, no persistente a largo plazo).
*   **Terceros:** No vendemos ni compartimos información con terceros con fines publicitarios.

---

## 3. Política de Uso (Términos de Servicio)

Al utilizar SlideHub, ya sea como presentador o espectador, aceptas las siguientes reglas:

1.  **Uso Aceptable:** SlideHub debe utilizarse para la gestión y sincronización de diapositivas en presentaciones profesionales, académicas o personales. 
2.  **Actividades Prohibidas:** 
    *   Subir contenido ilegal, ofensivo, que infrinja derechos de autor de terceros o que contenga malware.
    *   Abusar de las integraciones de IA (Gemini/Groq) intentando inyectar prompts maliciosos (prompt injection) o abusar de los límites de tasa (rate limits).
    *   Interferir o intentar eludir la seguridad y el flujo de los presentadores en la red o modificar el comportamiento del módulo de sincronización.
3.  **Control de Cuenta:** Usted es responsable de mantener la confidencialidad de sus contraseñas y cuentas. Las cuentas que violen nuestras políticas podrán ser suspendidas sin previo aviso.
4.  **Archivos de Google Drive:** La integración con Google Drive requiere permisos de lectura (`drive.readonly`). Solo accederemos a las carpetas y archivos explícitamente compartidos por el usuario con el fin de importar las diapositivas.

---

## 4. Exención de Responsabilidad (Disclaimer)

**El sistema SlideHub se proporciona "tal cual" (AS IS), sin garantía de ningún tipo, expresa o implícita.**

*   **Disponibilidad del Servicio:** No garantizamos un 100% de tiempo de actividad (uptime). El servicio depende de infraestructuras externas (Render, AWS, Aiven, MongoDB) y es susceptible a cortes de red, errores intermitentes u otras interrupciones imprevisibles.
*   **Errores e IA:** SlideHub utiliza Modelos de Lenguaje Grande (LLMs) como Gemini y Groq. El contenido generado ("Notas del presentador", "Deploy Tutor", etc.) puede contener errores, "alucinaciones" o información desactualizada. Es entera responsabilidad del presentador revisar y validar el contenido generado antes de exponerlo frente a una audiencia.
*   **Pérdida de Datos:** No nos hacemos responsables por la pérdida de presentaciones, imágenes o sesiones fallidas debidas a expiraciones de TTL en Redis (caché) u omisiones en bases de datos. Se recomienda mantener respaldos locales de sus repositorios y diapositivas PNG originales.
*   **Limitación de Responsabilidad:** En ningún caso los autores o titulares del copyright serán responsables por reclamos, daños u otras responsabilidades, ya sea en una acción de contrato, agravio o de otro tipo, que surjan de o en conexión con el software o el uso u otros tratos en el software.