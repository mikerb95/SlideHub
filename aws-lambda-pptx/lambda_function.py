import json
import os
import urllib.parse
import tempfile
import subprocess
import boto3
import fitz  # PyMuPDF
import requests

s3_client = boto3.client('s3')

WEBHOOK_URL    = os.environ.get('SLIDEHUB_WEBHOOK_URL')
WEBHOOK_SECRET = os.environ.get('SLIDEHUB_WEBHOOK_SECRET')
SOFFICE_PATH   = '/usr/bin/soffice'


def lambda_handler(event, context):
    print(f"Evento recibido: {json.dumps(event)}")

    try:
        record = event['Records'][0]
        bucket  = record['s3']['bucket']['name']
        raw_key = urllib.parse.unquote_plus(record['s3']['object']['key'])

        if not raw_key.startswith('raw-pptx/') or not raw_key.endswith('.pptx'):
            print(f"Ignorando archivo no válido: {raw_key}")
            return {'statusCode': 400, 'body': 'Ruta o formato no válido'}

        presentation_id = raw_key.split('/')[1].replace('.pptx', '')

        with tempfile.TemporaryDirectory() as temp_dir:
            file_path = os.path.join(temp_dir, f"{presentation_id}.pptx")
            pdf_path  = os.path.join(temp_dir, f"{presentation_id}.pdf")

            print(f"Descargando {raw_key} de {bucket}")
            s3_client.download_file(bucket, raw_key, file_path)

            print("Convirtiendo PPTX → PDF con LibreOffice...")
            result = subprocess.run([
                SOFFICE_PATH,
                '--headless',
                '--norestore',
                '--convert-to', 'pdf',
                '--outdir', temp_dir,
                file_path
            ], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
               env={**os.environ, 'HOME': '/tmp'})

            if result.returncode != 0:
                print(f"soffice stdout: {result.stdout.decode(errors='replace')[:1000]}")
                print(f"soffice stderr: {result.stderr.decode(errors='replace')[:2000]}")
                raise RuntimeError(f"LibreOffice falló con exit code {result.returncode}")

            if not os.path.exists(pdf_path):
                raise RuntimeError("La conversión a PDF falló silenciosamente.")

            print("Convirtiendo PDF → PNGs...")
            doc = fitz.open(pdf_path)
            total_slides = len(doc)

            for i in range(total_slides):
                page = doc.load_page(i)
                pix  = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0))
                png_path = os.path.join(temp_dir, f"Slide_{i+1}.PNG")
                pix.save(png_path)

                target_key = f"slides/{presentation_id}/{i+1}.png"
                s3_client.upload_file(
                    png_path, bucket, target_key,
                    ExtraArgs={'ContentType': 'image/png'}
                )

            print(f"Éxito: {total_slides} diapositivas subidas a S3.")

            s3_client.delete_object(Bucket=bucket, Key=raw_key)
            print(f"raw-pptx eliminado: {raw_key}")

            send_webhook(presentation_id, total_slides, "READY")
            return {'statusCode': 200, 'body': json.dumps({'total_slides': total_slides})}

    except Exception as e:
        print(f"Error procesando el archivo: {str(e)}")
        if 'presentation_id' in locals():
            send_webhook(presentation_id, 0, "FAILED", error=str(e))
        raise e


def send_webhook(presentation_id, slide_count, status, error=None):
    if not WEBHOOK_URL:
        print("WEBHOOK_URL no definida, omitiendo callback.")
        return

    try:
        response = requests.post(
            WEBHOOK_URL,
            json={"presentationId": presentation_id, "slideCount": slide_count,
                  "status": status, "error": error},
            headers={'X-Webhook-Secret': WEBHOOK_SECRET or ''},
            timeout=15,
            allow_redirects=True
        )
        print(f"Webhook enviado. Status: {response.status_code} URL: {response.url}")
        if not response.ok:
            print(f"  Error body: {response.text[:500]}")
    except requests.exceptions.RequestException as e:
        print(f"Fallo enviando webhook: {str(e)}")
