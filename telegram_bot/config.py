"""Configuration loaded from environment variables (never hardcode secrets)."""
import os


def _int(env_name: str, default: int) -> int:
    try:
        return int(os.environ.get(env_name, str(default)))
    except ValueError:
        return default


BOT_TOKEN = os.environ.get("BOT_TOKEN", "").strip()

ADMIN_ID = _int("ADMIN_ID", 0)

CLONE_SERVER_URL = os.environ.get("CLONE_SERVER_URL", "http://server:8000").rstrip("/")

DATA_DIR = os.environ.get("BOT_DATA_DIR", "data")
SAMPLES_DIR = os.path.join(DATA_DIR, "samples")

MIN_SAMPLE_SECONDS = _int("MIN_SAMPLE_SECONDS", 10)
MAX_SAMPLE_SECONDS = _int("MAX_SAMPLE_SECONDS", 30)
MAX_TEXT_LENGTH = _int("MAX_TEXT_LENGTH", 500)
DAILY_LIMIT_DEFAULT = _int("DAILY_LIMIT_DEFAULT", 5)
COOLDOWN_SECONDS = _int("COOLDOWN_SECONDS", 30)
HISTORY_LIMIT = _int("HISTORY_LIMIT", 10)

SUPPORTED_LANGUAGES = [
    ("ar", "العربية"),
    ("en", "English"),
    ("fr", "Français"),
    ("es", "Español"),
    ("de", "Deutsch"),
    ("tr", "Türkçe"),
    ("ru", "Русский"),
    ("hi", "हिन्दी"),
]


def validate() -> list:
    errors = []
    if not BOT_TOKEN:
        errors.append("BOT_TOKEN is not set (export BOT_TOKEN=...)")
    if not ADMIN_ID:
        errors.append("ADMIN_ID is not set (your numeric Telegram user id)")
    return errors
