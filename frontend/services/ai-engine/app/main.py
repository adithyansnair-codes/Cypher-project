import random
from fastapi import FastAPI, HTTPException
import os
import requests

app = FastAPI(title="CYPHER AI Engine")

GATEWAY_URL = os.getenv("GATEWAY_URL", "http://gateway:8080")
SERVICE_API_KEY = os.getenv(
    "SERVICE_API_KEY",
    "cypher-internal-dev-key"
)


def trigger_incident(payload: dict):
    try:
        response = requests.post(
            f"{GATEWAY_URL}/api/ingest/incident",
            headers={
                "X-Service-Key": SERVICE_API_KEY,
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=10,
        )
    except requests.RequestException as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Could not reach gateway: {exc}",
        )

    if not response.ok:
        raise HTTPException(
            status_code=response.status_code,
            detail=response.text,
        )

    return response.json()


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "ai-engine",
    }


@app.post("/demo/{scenario}")
def demo(scenario: str, camera_id: int = 1):

    if scenario == "threat":
        # Properly indented random priority logic (3:1:1 ratio)
        priorities = ["MEDIUM", "HIGH", "CRITICAL"]
        weights = [3, 1, 1]
        random_priority = random.choices(priorities, weights=weights, k=1)[0]

        payload = {
            "cameraId": camera_id,
            "incidentType": "THREAT",
            "priority": random_priority,  # Dynamically injected string
            "assetType": "PERSON",
            "confidence": 0.96,
            "summary": "Potential security threat detected",
            "snapshotUrl": None,
            "boundingBox": {
                "x": 0.42,
                "y": 0.28,
                "width": 0.18,
                "height": 0.42,
            },
            "metadata": {
                "source": "ai-demo",
                "scenario": "threat",
            },
            "detections": [],
        }

    elif scenario == "health":
        payload = {
            "cameraId": camera_id,
            "incidentType": "HEALTH",
            "priority": random_priority,
            "assetType": "CAMERA",
            "confidence": 0.93,
            "summary": "Camera health anomaly detected",
            "snapshotUrl": None,
            "boundingBox": {},
            "metadata": {
                "source": "ai-demo",
                "scenario": "health",
            },
            "detections": [],
        }

    else:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown scenario: {scenario}",
        )

    return trigger_incident(payload)
