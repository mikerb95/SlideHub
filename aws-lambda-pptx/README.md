# SlideHub PPTX to PNG Converter (AWS Lambda)

Este módulo contiene el script serverless que procesa archivos de PowerPoint (.pptx), descargándolos desde AWS S3, usando LibreOffice (para convertir de PPTX a PDF) y PyMuPDF (para separar el PDF a imagenes estáticas). Finalmente, notifica vía Webhook al Backend de SlideHub.

## Arquitectura

1. El usuario sube `archivo.pptx` mediante el backend de SlideHub a `raw-pptx/{id}.pptx` en S3.
2. Amazon S3 tiene un EventTrigger integrado configurado para notificar a la función Lambda (ObjectCreated:Put).
3. La función Lambda inicia: 
   - Lee el evento y descarga el pptx.
   - Lo pasa por `soffice` (headless) generando un `pdf`.
   - Lee el PDF con `fitz` y genera un PNG en calidad 2.0.
   - Sube a S3 las imagenes a `presentations/{id}/Slide_X.PNG`.
   - Transmite un HTTP POST a `{TU_APP}/api/webhooks/pptx-conversion`.

## Requisitos y Configuración de AWS Lambda

### 1. Variables de Entorno (Environment variables)
Dentro de la consola de tu Lambda, debes configurar:
- `SLIDEHUB_WEBHOOK_URL`: `https://slidehub-tu-dominio.com/api/webhooks/pptx-conversion`
- `SLIDEHUB_WEBHOOK_SECRET`: El token de autorización maestro acordado.
- (Opcional) `LIBREOFFICE_PATH`: Si cambiaste la ruta de la Layer. Normalment es `/opt/instdir/program/soffice.bin`

### 2. Memoria y Timeout
Dado que iniciar y procesar en memoria LibreOffice consume recursos, debes setear la **Configuración Básica (Basic settings)** de la Lambda en AWS:
- **Max Memory (Memoria):** 1024 MB o 2048 MB recomendado (PPTXs pesados necesitan RAM).
- **Timeout:** 1 minuto a 3 minutos (por si el archivo tiene más de 100 diapositivas).

### 3. Layers requeridos (Capas)

La función Lambda nativa de Python no incluye LibreOffice, así que debes montarlo a través de Lambda Layers (Capas):

1. **aws-lambda-libreoffice:** (Una capa pública popular para la ejecución precompilada del motor). Por ejemplo la mantenida por `shelfio` (v7.4+).
2. **PyMuPDF:** (Puedes instalar las librerias empaquetando el directorio local y subiéndolo como `.zip` junto a `lambda_function.py`).

### Estructura de despliegue local:
```bash
pip install -r requirements.txt -t package/
cp lambda_function.py package/
cd package/ && zip -r9 ../function.zip .
```
Sube `<function.zip>` al dashboard de AWS Lambda.

