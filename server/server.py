# Cope Saund Mahdi — lightweight voice-cloning server
# REST API over Chatterbox (MIT, Resemble AI) with optional SoproTTS (Apache-2.0) engine.
# Run: uvicorn server:app --host 0.0.0.0 --port 8000
import io
import os
import shutil
import subprocess
import tempfile
import threading

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse

ENGINE = os.environ.get("COPE_SAUND_ENGINE", "chatterbox").lower()  # chatterbox | sopro

app = FastAPI(title="Cope Saund Mahdi Clone Server", version="1.0.0")

_lock = threading.Lock()
_model = None
_model_kind = None

# Languages supported by Chatterbox Multilingual (subset exposed by the app)
CHATTERBOX_LANGS = {
    "en", "ar", "es", "fr", "de", "it", "pt", "ru", "tr", "hi", "zh", "ja", "ko", "nl", "pl"
}


def _load_model(language: str):
    """Lazily load the TTS engine. Returns (kind, model)."""
    global _model, _model_kind
    with _lock:
        if _model is None:
            if ENGINE == "sopro":
                from sopro import SoproTTS
                _model = SoproTTS.from_pretrained("samuel-vitorino/sopro", device="cpu")
                _model_kind = "sopro"
            else:
                # Multilingual model handles all supported languages including Arabic.
                from chatterbox.mtl_tts import ChatterboxMultilingualTTS
                _model = ChatterboxMultilingualTTS.from_pretrained(device="cpu")
                _model_kind = "mtl"
        return _model_kind, _model


def _run_engine(kind, model, text, ref_path, language, exaggeration):
    import torch
    import torchaudio
    if kind == "sopro":
        ref = model.prepare_reference(ref_audio_path=ref_path)
        chunks = [c for c in model.stream(text, ref=ref)]
        wav = torch.cat(chunks, dim=-1)
        sr = model.sr if hasattr(model, "sr") else 24000
        return wav, sr
    wav = model.generate(
        text,
        language_id=language,
        audio_prompt_path=ref_path,
        exaggeration=float(exaggeration),
    )
    return wav, model.sr


def _apply_speed(wav_path, speed):
    """Post-process speed using ffmpeg's atempo when available."""
    if abs(speed - 1.0) < 0.05 or shutil.which("ffmpeg") is None:
        return wav_path
    processed = wav_path + ".tempo.wav"
    cmd = ["ffmpeg", "-y", "-i", wav_path, "-filter:a", f"atempo={speed}", processed]
    if subprocess.run(cmd, capture_output=True).returncode == 0:
        os.replace(processed, wav_path)
    else:
        if os.path.exists(processed):
            os.remove(processed)
    return wav_path


@app.get("/health")
def health():
    return {
        "status": "ok",
        "engine": ENGINE,
        "languages": sorted(CHATTERBOX_LANGS) if ENGINE == "chatterbox" else ["en"],
        "app": "cope-saund-mahdi",
    }


@app.post("/clone")
def clone(
    ref: UploadFile = File(...),
    text: str = Form(...),
    language: str = Form("auto"),
    speed: float = Form(1.0),
    exaggeration: float = Form(1.25),
):
    if not text.strip():
        raise HTTPException(status_code=400, detail="Empty text")
    if ENGINE == "chatterbox" and language != "auto" and language not in CHATTERBOX_LANGS:
        language = "en"

    tmpdir = tempfile.mkdtemp(prefix="cope_saund_")
    try:
        ref_path = os.path.join(tmpdir, "ref" + os.path.splitext(ref.filename or "ref.wav")[1])
        with open(ref_path, "wb") as f:
            f.write(ref.file.read())

        kind, model = _load_model("en" if language == "auto" else language)
        wav, sr = _run_engine(kind, model, text.strip(), ref_path, language, exaggeration)

        wav_path = os.path.join(tmpdir, "out.wav")
        import torchaudio
        torchaudio.save(wav_path, wav.cpu(), sr)
        wav_path = _apply_speed(wav_path, float(speed))

        with open(wav_path, "rb") as f:
            data = f.read()
        return StreamingResponse(io.BytesIO(data), media_type="audio/wav")
    except HTTPException:
        raise
    except Exception as exc:  # pragma: no cover
        return JSONResponse(status_code=500, content={"detail": str(exc)})
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)
