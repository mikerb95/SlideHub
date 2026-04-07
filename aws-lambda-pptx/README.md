# SlideHub PPTX to PNG Converter (AWS Lambda)

Este módulo contiene el script serverless que procesa archivos de PowerPoint (.pptx), descargándolos desde AWS S3, usando LibreOffice (para convertir de PPTX a PDF) y PyMuPDF (para separar el PDF a imagenes estáticas). Finalmente, notifica vía Webhook al Backend de SlideHub.

## Arquitectura

1. El usuario sube `archivo.pptx` mediante el backend de SlideHub a `raw-pptx/{id}.pptx` en S3.
2. Amazon S3 tiene un EventTrigger integrado configurado para notificar a la función Lambda (ObjectCreated:Put).
3. La función Lambda inicia:
   - Lee el evento y descarga el pptx.
   - Lo pasa por `soffice` (headless) generando un `pdf`.
   - Lee el PDF con `fitz` y genera un PNG en calidad 2.0.
   - Sube a S3 las imágenes a `slides/{presentationId}/{n}.png`.
   - Transmite un HTTP POST a `{TU_APP}/api/webhooks/pptx-conversion`.

## Despliegue — Imagen de Contenedor (recomendado)

El enfoque actual usa una **imagen Docker** publicada en Amazon ECR. LibreOffice se instala directamente en la imagen, sin necesidad de Lambda Layers.

### 1. Construir y publicar la imagen

```bash
# Autenticarse en ECR
aws ecr get-login-password --region us-east-2 | \
  docker login --username AWS --password-stdin \
  <ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com

# Crear repositorio (primera vez)
aws ecr create-repository --repository-name slidehub-pptx-lambda --region us-east-2

# Build + tag + push
docker build -t slidehub-pptx-lambda .
docker tag slidehub-pptx-lambda:latest \
  <ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/slidehub-pptx-lambda:latest
docker push \
  <ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/slidehub-pptx-lambda:latest
```

### 2. Crear / actualizar el Lambda

En AWS Console → Lambda → **Create function** → **Container image**:
- Image URI: `<ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/slidehub-pptx-lambda:latest`
- Architecture: `x86_64`

Para actualizar una función existente:

```bash
aws lambda update-function-code \
  --function-name slidehub-pptx-converter \
  --image-uri <ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/slidehub-pptx-lambda:latest \
  --region us-east-2
```

### 3. Variables de Entorno (Environment variables)

Dentro de la consola de tu Lambda → Configuration → Environment variables:

| Variable | Valor |
|---|---|
| `SLIDEHUB_WEBHOOK_URL` | `https://tu-dominio.onrender.com/api/webhooks/pptx-conversion` |
| `SLIDEHUB_WEBHOOK_SECRET` | El token acordado con el backend |
| `LIBREOFFICE_PATH` | `/usr/bin/soffice` (default en imagen de contenedor) |

> **Importante con el webhook**: usa siempre la URL con `https://`. Si apuntas a `http://` Render redirige a `https://` y el POST puede fallar.

### 4. Configuración básica (Basic settings)

- **Memory**: 2048 MB (LibreOffice es pesado; 1024 MB puede ser insuficiente para PPTX grandes)
- **Timeout**: 3 minutos (PPTX con 100+ slides puede tardar)
- **Architecture**: x86_64

### 5. Permisos IAM del Lambda

El rol de ejecución necesita:
```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
  "Resource": "arn:aws:s3:::slidehub-assets/*"
}
```

### 6. S3 Event Trigger

En S3 → `slidehub-assets` → Properties → Event notifications:
- **Event types**: `s3:ObjectCreated:Put`
- **Prefix**: `raw-pptx/`
- **Suffix**: `.pptx`
- **Destination**: Lambda → `slidehub-pptx-converter`

## Depuración del webhook (302 redirect)

Si el log muestra un error 302 al enviar el webhook:
1. Verifica que `SLIDEHUB_WEBHOOK_URL` use `https://` (no `http://`)
2. Verifica que la URL no tenga trailing slash: `.../pptx-conversion` ✓ vs `.../pptx-conversion/` ✗
3. El log de Lambda ahora imprime la cadena de redirects para facilitar el diagnóstico
