FROM public.ecr.aws/lambda/python:3.10 AS builder

WORKDIR /build
COPY requirements.txt .
RUN pip install -r requirements.txt -t ./package/
COPY lambda_function.py ./package/
RUN cd package && zip -r9 /build/function.zip .
