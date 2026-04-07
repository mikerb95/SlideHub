#!/bin/bash
# Crea el usuario DEVELOPER en SlideHub
# Uso: ./create-developer.sh

BASE_URL="${BASE_URL:-https://slide.lat}"
SECRET="${MGR_BOOTSTRAP_SECRET}"

if [ -z "$SECRET" ]; then
  echo "Error: MGR_BOOTSTRAP_SECRET no está definida."
  echo "Ejecútalo así: MGR_BOOTSTRAP_SECRET=tu-secret ./create-developer.sh"
  exit 1
fi

read -p "Username: " USERNAME
read -p "Email: " EMAIL
read -s -p "Password: " PASSWORD
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/mgr/bootstrap" \
  --data-urlencode "secret=${SECRET}" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  --data-urlencode "email=${EMAIL}")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo "HTTP $HTTP_CODE: $BODY"
