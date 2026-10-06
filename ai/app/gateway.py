"""Thin client for the CYPHER gateway's incident-ingest API.

Keeps the AI engine decoupled from how the gateway stores things: this module
only knows the HTTP contract (MoSCoW M-05).
"""
from __future__ import annotations

import logging
import time
from typing import Any

import requests

from .config import settings

log = logging.getLogger("cypher.gateway")


class GatewayClient:
    def __init__(self) -> None:
        self._token: str | None = None
        self._token_at: float = 0.0
        self._camera_id: int | None = settings.CAMERA_ID

    # ------------------------------------------------------------------ auth
    def _login(self) -> str:
        url = f"{settings.GATEWAY_URL}/auth/login"
        response = requests.post(
            url,
            json={
                "email": settings.SERVICE_EMAIL,
                "password": settings.SERVICE_PASSWORD,
            },
            timeout=10,
        )
        response.raise_for_status()
        token = response.json().get("token")
        if not token:
            raise RuntimeError(f"login returned no token: {response.text[:200]}")
        self._token = token
        self._token_at = time.time()
        log.info("authenticated with the gateway as %s", settings.SERVICE_EMAIL)
        return token

    def _auth_header(self) -> dict[str, str]:
        # Re-login every 12 hours; the token itself lasts 24.
        if self._token is None or (time.time() - self._token_at) > 12 * 3600:
            self._login()
        return {"Authorization": f"Bearer {self._token}"}

    # --------------------------------------------------------------- cameras
    def cameras(self) -> list[dict[str, Any]]:
        response = requests.get(
            f"{settings.GATEWAY_URL}/api/cameras",
            headers=self._auth_header(),
            timeout=10,
        )
        response.raise_for_status()
        return response.json()

    def default_camera_id(self) -> int:
        """The camera this engine reports against."""
        if self._camera_id is not None:
            return self._camera_id
        cameras = self.cameras()
        if not cameras:
            raise RuntimeError(
                "no cameras registered -- run the seed migration or create one first"
            )
        self._camera_id = int(cameras[0]["cameraId"])
        log.info("reporting against camera %s", self._camera_id)
        return self._camera_id

    # ---------------------------------------------------------------- ingest
    def report_incident(
        self,
        title: str,
        detected_object: str,
        confidence: float,
        description: str | None = None,
        camera_id: int | None = None,
    ) -> dict[str, Any]:
        """Submit a detection. The gateway derives CRITICAL for weapons/fire."""
        payload = {
            "cameraId": camera_id if camera_id is not None else self.default_camera_id(),
            "title": title,
            "description": description,
            "detectedObject": detected_object,
            "confidence": confidence,
        }
        response = requests.post(
            f"{settings.GATEWAY_URL}/api/incidents/ingest",
            json=payload,
            headers=self._auth_header(),
            timeout=10,
        )
        response.raise_for_status()
        incident = response.json()
        log.info(
            "incident #%s raised: %s (%s, %.0f%%)",
            incident.get("incidentId"),
            incident.get("title"),
            incident.get("severity"),
            confidence,
        )
        return incident

    # ----------------------------------------------------------------- health
    def healthy(self) -> bool:
        try:
            return requests.get(f"{settings.GATEWAY_URL}/health", timeout=5).ok
        except requests.RequestException:
            return False
