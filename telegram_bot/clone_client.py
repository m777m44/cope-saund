"""Client for the self-hosted Cope Saund clone server (server/server.py)."""
import os

import requests


class CloneError(Exception):
    pass


class CloneClient:
    def __init__(self, base_url: str, timeout: int = 300):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def health(self) -> dict:
        try:
            response = requests.get(f"{self.base_url}/health", timeout=10)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as exc:
            raise CloneError(f"Clone server unreachable: {exc}") from exc

    def clone(
        self,
        sample_path: str,
        text: str,
        language: str = "auto",
        speed: float = 1.0,
        exaggeration: float = 1.25,
    ) -> bytes:
        """POST /clone with a reference sample; returns WAV bytes."""
        if not os.path.isfile(sample_path):
            raise CloneError("Sample file is missing")
        try:
            with open(sample_path, "rb") as fh:
                response = requests.post(
                    f"{self.base_url}/clone",
                    files={"ref": (os.path.basename(sample_path), fh, "audio/*")},
                    data={
                        "text": text,
                        "language": language,
                        "speed": float(speed),
                        "exaggeration": float(exaggeration),
                    },
                    timeout=self.timeout,
                )
        except requests.RequestException as exc:
            raise CloneError(f"Clone request failed: {exc}") from exc
        if response.status_code == 200:
            if not response.content:
                raise CloneError("Clone server returned an empty audio file")
            return response.content
        detail = ""
        try:
            detail = response.json().get("detail", "")
        except ValueError:
            detail = response.text[:200]
        raise CloneError(f"Clone failed ({response.status_code}): {detail}")
