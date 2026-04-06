FROM public.ecr.aws/lambda/python:3.10 AS builder

RUN yum install -y zip

WORKDIR /build
COPY requirements.txt .
RUN pip install -r requirements.txt -t ./package/
COPY lambda_function.py ./package/

WORKDIR /build/package
RUN zip -r9 /build/function.zip .
