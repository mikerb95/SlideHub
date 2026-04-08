import requests
import os
import json

GROQ_API_KEY = os.environ.get("GROQ_API_KEY")
if not GROQ_API_KEY:
    print("NO GROQ KEY")
    exit(1)

url = "https://api.groq.com/openai/v1/chat/completions"
headers = {
    "Authorization": f"Bearer {GROQ_API_KEY}",
    "Content-Type": "application/json"
}

payload = {
    "model": "llama3-8b-8192",
    "messages": [
        {"role": "system", "content": "You are a test."},
        {"role": "user", "content": "Hello"}
    ],
    "temperature": 0.7,
    "max_tokens": 1024
}

try:
    response = requests.post(url, headers=headers, json=payload)
    print(response.status_code)
    print(response.text)
except Exception as e:
    print(e)
