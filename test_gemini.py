import requests
import os
import json
import base64

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    print("NO GEMINI KEY")
    exit(1)

url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={GEMINI_API_KEY}"
headers = {
    "Content-Type": "application/json"
}

with open("icon.png", "wb") as f:
    # Just a small 1x1 png
    f.write(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="))

with open("icon.png", "rb") as f:
    b64 = base64.b64encode(f.read()).decode("utf-8")

payload = {
    "contents": [
        {
            "parts": [
                {
                    "inlineData": {
                        "mimeType": "image/png",
                        "data": b64
                    }
                },
                {
                    "text": "Analiza esta diapositiva de presentación."
                }
            ]
        }
    ]
}

try:
    response = requests.post(url, headers=headers, json=payload)
    print(response.status_code)
    print(response.text)
except Exception as e:
    print(e)
