#!/usr/bin/env bash
# Downloads the voice-cloning model weights from Hugging Face into ./models
# Weights are NOT committed to the repository.
set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)/models"
mkdir -p chatterbox sopro

echo "==> Downloading Chatterbox Multilingual (MIT) — required by server/"
python3 - <<'PY'
from huggingface_hub import snapshot_download
snapshot_download("ResembleAI/chatterbox", local_dir="chatterbox", allow_patterns=["*.pt", "*.safetensors", "*.json", "*.yaml"])
print("chatterbox -> models/chatterbox")
PY

echo "==> Optional: SoproTTS (Apache-2.0, English-only, CPU-friendly)"
python3 - <<'PY'
try:
    from huggingface_hub import snapshot_download
    snapshot_download("samuel-vitorino/sopro", local_dir="sopro")
    print("sopro -> models/sopro")
except Exception as exc:
    print("skipped sopro:", exc)
PY


echo "==> Optional: ice-012-audio (darkps) — CC-BY-NC-4.0, NON-COMMERCIAL use only (see NOTICES.md)"
python3 - <<'PY'
try:
    from huggingface_hub import snapshot_download
    snapshot_download("darkps/ice-012-audio", local_dir="ice-012-audio",
                      allow_patterns=["*.json", "*.py", "*.safetensors", "*.txt", "*.jinja"])
    print("ice-012-audio -> models/ice-012-audio (license: CC-BY-NC-4.0)")
except Exception as exc:
    print("skipped ice-012-audio:", exc)
PY

echo "==> Done."
