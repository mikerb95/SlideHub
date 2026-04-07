import json
import os
import io
import tarfile
import boto3
import urllib.parse
import tempfile
import subprocess
import fitz  # PyMuPDF
import requests
import brotli as brotli_lib

s3_client = boto3.client('s3')

WEBHOOK_URL    = os.environ.get('SLIDEHUB_WEBHOOK_URL')
WEBHOOK_SECRET = os.environ.get('SLIDEHUB_WEBHOOK_SECRET')

# Paths del layer shelfio-brotli
_BROTLI_ARCHIVE   = '/opt/lo.tar.br'
_SOFFICE_EXTRACTED = '/tmp/instdir/program/soffice.bin'


def _ensure_libreoffice():
    """
    El layer shelfio almacena LibreOffice como lo.tar.br.
    Lo extraemos a /tmp/ en el primer cold start; las invocaciones
    siguientes reutilizan el contenido ya extraído.
    """
    if os.path.exists(_SOFFICE_EXTRACTED):
        return _SOFFICE_EXTRACTED

    if not os.path.exists(_BROTLI_ARCHIVE):
        raise FileNotFoundError(
            f"Layer brotli no encontrado en {_BROTLI_ARCHIVE}. "
            f"/opt contiene: {os.listdir('/opt') if os.path.exists('/opt') else '(vacío)'}"
        )

    print(f"Cold start: extrayendo LibreOffice desde {_BROTLI_ARCHIVE}...")

    # 1. Descomprimir brotli → tar (escribir a disco para no saturar RAM)
    tar_path = '/tmp/lo.tar'
    with open(_BROTLI_ARCHIVE, 'rb') as f:
        compressed = f.read()
    with open(tar_path, 'wb') as f:
        f.write(brotli_lib.decompress(compressed))
    del compressed  # liberar RAM

    # 2. Extraer tar a /tmp/
    with tarfile.open(tar_path) as tar:
        tar.extractall('/tmp/')
    os.remove(tar_path)

    if not os.path.exists(_SOFFICE_EXTRACTED):
        # Buscar dónde quedó realmente
        for root, dirs, files in os.walk('/tmp'):
            if 'soffice.bin' in files:
                found = os.path.join(root, 'soffice.bin')
                print(f"soffice.bin encontrado en: {found}")
                os.chmod(found, 0o755)
                return found
        raise FileNotFoundError("soffice.bin no encontrado tras la extracción del layer.")

    os.chmod(_SOFFICE_EXTRACTED, 0o755)
    print(f"LibreOffice listo en: {_SOFFICE_EXTRACTED}")
    return _SOFFICE_EXTRACTED


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

        # Resolver path de soffice (extrae del layer si es necesario)
        lo_path = _ensure_libreoffice()

        with tempfile.TemporaryDirectory() as temp_dir:
            file_path = os.path.join(temp_dir, f"{presentation_id}.pptx")
            pdf_path  = os.path.join(temp_dir, f"{presentation_id}.pdf")

            print(f"Descargando {raw_key} de {bucket}")
            s3_client.download_file(bucket, raw_key, file_path)

            print(f"Convirtiendo PPTX → PDF con LibreOffice ({lo_path})...")
            lo_env = os.environ.copy()
            lo_env['HOME'] = '/tmp'
            lo_env['LD_LIBRARY_PATH'] = '/tmp/instdir/program:' + lo_env.get('LD_LIBRARY_PATH', '')
            lo_env['UNO_PATH'] = '/tmp/instdir/program'
            lo_env['PATH'] = '/tmp/instdir/program:' + lo_env.get('PATH', '')
            result = subprocess.run([
                lo_path,
                '--headless', '--invisible', '--nodefault',
                '--nofirststartwizard',
                '-env:UserInstallation=file:///tmp/lo-user-profile',
                '--convert-to', 'pdf',
                '--outdir', temp_dir,
                file_path
            ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=lo_env)
            if result.returncode != 0:
                print(f"soffice stderr: {result.stderr.decode(errors='replace')}")
                raise subprocess.CalledProcessError(result.returncode, result.args)

            if not os.path.exists(pdf_path):
                raise Exception("La conversión a PDF falló silenciosamente.")

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
