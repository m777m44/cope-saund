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

ENGINE = os.environ.get("COPE_SAUND_ENGINE", "chatterbox").lower()  # chatterbox | sopro | ice
ICE_MODEL_ID = "darkps/ice-012-audio"       # CC-BY-NC-4.0 (non-commercial use only)
ICE_AI_MODEL_ID = "darkps/ice-AI"            # Apache-2.0; optional text-enhancer
ICE_AI_ENABLE = os.environ.get("ICE_AI_ENABLE", "0") == "1"

app = FastAPI(title="Cope Saund Mahdi Clone Server", version="1.1.0")

_lock = threading.Lock()
_model = None
_model_kind = None
_ice_ai_model = None

# Languages supported by Chatterbox Multilingual (subset exposed by the app)
CHATTERBOX_LANGS = {
    "en", "ar", "es", "fr", "de", "it", "pt", "ru", "tr", "hi", "zh", "ja", "ko", "nl", "pl"
}

# App language codes -> ice-012-audio language names (590 supported by the model)
ICE_LANG_MAP = {
    "en": "english",
    "ar": "standard arabic",
    "fr": "french",
    "es": "spanish",
    "de": "german",
    "tr": "turkish",
    "ru": "russian",
    "hi": "hindi",
    # common Arabic dialects (ice-012-audio native support)
    "eg": "egyptian lahgtna", "sa": "saudi lahgtna", "sy": "syrian lahgtna",
    "lb": "lebanese lahgtna", "iq": "iraqi lahgtna", "ma": "moroccan lahgtna",
    "dz": "algerian lahgtna", "tn": "tunisian lahgtna", "ly": "libyan lahgtna",
    "ps": "palestinian lahgtna", "ye": "yemeni lahgtna", "bh": "bahraini lahgtna",
    "sd": "sudanese lahgtna",
}
ICE_VALID_IDS = set(ICE_LANG_MAP.keys())


def _load_model(language: str):
    """Lazily load the TTS engine. Returns (kind, model)."""
    global _model, _model_kind
    with _lock:
        if _model is None:
            if ENGINE == "sopro":
                from sopro import SoproTTS
                _model = SoproTTS.from_pretrained("samuel-vitorino/sopro", device="cpu")
                _model_kind = "sopro"
            elif ENGINE == "ice":
                import torch
                from transformers import AutoModelForCausalLM
                # trust_remote_code executes the model's published modeling code.
                # ice-012-audio is CC-BY-NC-4.0 -> non-commercial use only (see NOTICES.md).
                _model = AutoModelForCausalLM.from_pretrained(
                    ICE_MODEL_ID, trust_remote_code=True, torch_dtype=torch.float32
                )
                _model.eval()
                _model_kind = "ice"
            else:
                # Multilingual model handles all supported languages including Arabic.
                from chatterbox.mtl_tts import ChatterboxMultilingualTTS
                _model = ChatterboxMultilingualTTS.from_pretrained(device="cpu")
                _model_kind = "mtl"
        return _model_kind, _model


def _run_engine(kind, model, text, ref_path, language, exaggeration, instruct=None):
    import torch
    import torchaudio
    if kind == "sopro":
        ref = model.prepare_reference(ref_audio_path=ref_path)
        chunks = [c for c in model.stream(text, ref=ref)]
        wav = torch.cat(chunks, dim=-1)
        sr = model.sr if hasattr(model, "sr") else 24000
        return wav, sr
    if kind == "ice":
        # ice-012-audio: reference-based cloning. "auto" -> language-agnostic mode.
        ice_lang = None if language in ("auto", None) else ICE_LANG_MAP.get(language, None)
        audios = model.generate(
            text=text,
            language=ice_lang,
            ref_audio=ref_path,
            speed=float(speed),
            instruct=(instruct or None),
        )
        wav = torch.from_numpy(audios[0])
        if wav.ndim == 1:
            wav = wav.unsqueeze(0)
        sr = int(model.sampling_rate)
        return wav, sr
    wav = model.generate(
        text,
        language_id=language,
        audio_prompt_path=ref_path,
        exaggeration=float(exaggeration),
    )
    return wav, model.sr


def _normalize_ref(ref_path: str) -> str:
    """Convert any input audio (mp3/m4a/ogg/flac/amr/wav) to 16k mono WAV when ffmpeg is available."""
    if shutil.which("ffmpeg") is None or ref_path.lower().endswith(".wav"):
        return ref_path
    wav_path = os.path.splitext(ref_path)[0] + ".conv.wav"
    cmd = ["ffmpeg", "-y", "-i", ref_path, "-ac", "1", "-ar", "16000", wav_path]
    if subprocess.run(cmd, capture_output=True).returncode == 0 and os.path.getsize(wav_path) > 0:
        return wav_path
    if os.path.exists(wav_path):
        os.remove(wav_path)
    return ref_path


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
    langs = sorted(CHATTERBOX_LANGS) if ENGINE == "chatterbox" else (
        sorted(ICE_VALID_IDS) if ENGINE == "ice" else ["en"]
    )
    return {
        "status": "ok",
        "engine": ENGINE,
        "languages": langs,
        "text_enhancer": ICE_AI_ENABLE,
        "app": "cope-saund-mahdi",
    }


@app.post("/clone")
def clone(
    ref: UploadFile = File(...),
    text: str = Form(...),
    language: str = Form("auto"),
    speed: float = Form(1.0),
    exaggeration: float = Form(1.25),
    instruct: str = Form(""),
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
        ref_path = _normalize_ref(ref_path)  # auto-detect/convert any audio format

        kind, model = _load_model("en" if language == "auto" else language)
        wav, sr = _run_engine(
            kind, model, text.strip(), ref_path, language, exaggeration, instruct
        )

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


# ---------------------------------------------------------------- text enhance
# Optional (Apache-2.0 model, heavy 8B): normalizes/cleans the text before cloning
# (numbers, punctuation, dialect). Enable with ICE_AI_ENABLE=1.
@app.post("/enhance")
def enhance(payload: dict):
    if not ICE_AI_ENABLE:
        return JSONResponse(status_code=501, content={"detail": "ice-AI enhancer disabled (set ICE_AI_ENABLE=1)"})
    text = str(payload.get("text", "")).strip()
    if not text:
        raise HTTPException(status_code=400, detail="Empty text")
    global _ice_ai_model
    with _lock:
        if _ice_ai_model is None:
            from transformers import pipeline
            _ice_ai_model = pipeline(
                "text-generation", model=ICE_AI_MODEL_ID, torch_dtype="auto"
            )
    try:
        out = _ice_ai_model(
            [
                {"role": "system", "content": "تنظيف وتحسين النص لقراءته بصوت مركب: صحح الإملاء، بسّط الأرقام، احافظ على المعنى واللهجة حرفيًا. أعد النص فقط."},
                {"role": "user", "content": text[:2000]},
            ],
            max_new_tokens=1200,
        )
        improved = out[0]["generated_text"][-1]["content"].strip()
        return {"text": improved or text}
    except Exception as exc:  # pragma: no cover
        return JSONResponse(status_code=500, content={"detail": str(exc)})
