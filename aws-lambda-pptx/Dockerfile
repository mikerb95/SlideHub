FROM public.ecr.aws/lambda/python:3.12

# LibreOffice + fuentes básicas para rendering correcto de PPTX
RUN dnf install -y \
        libreoffice-impress \
        libreoffice-writer \
        libreoffice-draw \
        liberation-fonts \
        google-noto-fonts-common \
    && dnf clean all \
    && rm -rf /var/cache/dnf

# Dependencias Python
COPY requirements.txt .
RUN pip install -r requirements.txt --no-cache-dir

COPY lambda_function.py .

# soffice estará en /usr/bin/soffice tras dnf install
ENV LIBREOFFICE_PATH=/usr/bin/soffice

CMD ["lambda_function.lambda_handler"]
