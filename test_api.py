import urllib.request
import json

url = "http://127.0.0"
for i in range(5):
    try:
        req = urllib.request.Request(url, method="POST")
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            print(f"Request {i+1}: Priority -> {data.get('priority')}")
    except Exception as e:
        print(f"Request {i+1} failed: {e}")
