"""JSON-based persistent storage for the Telegram bot (thread-safe, atomic writes)."""
import json
import os
import threading
import time
from datetime import datetime, timezone

from config import COOLDOWN_SECONDS, HISTORY_LIMIT

USER_FILE = "users.json"
SETTINGS_FILE = "settings.json"
STATS_FILE = "stats.json"


def _now() -> float:
    return time.time()


def _today() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d")


class Storage:
    def __init__(self, data_dir: str):
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)
        self._lock = threading.RLock()
        self._users = self._load(USER_FILE, {})
        self._settings = self._load(
            SETTINGS_FILE,
            {
                "daily_limit_free": 5,
                "allowed": True,
                "forced_channels": [],
            },
        )
        self._stats = self._load(
            STATS_FILE,
            {"total_clones": 0, "started_at": _now(), "chars_processed": 0},
        )

    # ---- low-level helpers -------------------------------------------------
    def _path(self, name: str) -> str:
        return os.path.join(self.data_dir, name)

    def _load(self, name: str, default):
        path = self._path(name)
        if not os.path.exists(path):
            return default
        try:
            with open(path, "r", encoding="utf-8") as fh:
                return json.load(fh)
        except (OSError, ValueError):
            return default

    def _save(self, name: str, data):
        path = self._path(name)
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
        os.replace(tmp, path)

    # ---- users --------------------------------------------------------------
    def user(self, user_id: int) -> dict:
        with self._lock:
            return self._users.get(str(user_id), {})

    def ensure_user(self, user_id: int, first_name: str = "", username: str = "") -> dict:
        with self._lock:
            uid = str(user_id)
            if uid not in self._users:
                self._users[uid] = {
                    "first_name": first_name,
                    "username": username,
                    "joined_at": _now(),
                    "is_paid": False,
                    "quota_date": _today(),
                    "quota_used": 0,
                    "last_clone_at": 0.0,
                    "history": [],
                }
                self._save(USER_FILE, self._users)
            else:
                changed = False
                userdata = self._users[uid]
                if first_name and userdata.get("first_name") != first_name:
                    userdata["first_name"] = first_name
                    changed = True
                if username and userdata.get("username") != username:
                    userdata["username"] = username
                    changed = True
                if changed:
                    self._save(USER_FILE, self._users)
            return self._users[uid]

    def set_paid(self, user_id: int, paid: bool) -> bool:
        with self._lock:
            uid = str(user_id)
            if uid not in self._users:
                return False
            self._users[uid]["is_paid"] = bool(paid)
            if paid:
                self._users[uid]["quota_date"] = _today()
                self._users[uid]["quota_used"] = 0
            self._save(USER_FILE, self._users)
            return True

    def paid_user_ids(self) -> list:
        with self._lock:
            return [uid for uid, u in self._users.items() if u.get("is_paid")]

    def all_user_ids(self) -> list:
        with self._lock:
            return list(self._users.keys())

    def users_count(self) -> int:
        with self._lock:
            return len(self._users)

    # ---- quota / cooldown ---------------------------------------------------
    def daily_limit(self, user_id: int) -> int:
        with self._lock:
            userdata = self.user(user_id)
            if userdata.get("is_paid"):
                return 10_000
            return int(self._settings.get("daily_limit_free", 5))

    def set_daily_limit(self, limit: int):
        with self._lock:
            self._settings["daily_limit_free"] = max(0, int(limit))
            self._save(SETTINGS_FILE, self._settings)

    def quota_check(self, user_id: int):
        """Returns (allowed, remaining, limit)."""
        with self._lock:
            userdata = self._users.get(str(user_id))
            if userdata is None:
                return True, 0, 0
            limit = self.daily_limit(user_id)
            today = _today()
            if userdata.get("quota_date") != today:
                userdata["quota_date"] = today
                userdata["quota_used"] = 0
                self._save(USER_FILE, self._users)
            used = int(userdata.get("quota_used", 0))
            return used < limit, max(0, limit - used), limit

    def cooldown_remaining(self, user_id: int) -> int:
        with self._lock:
            userdata = self._users.get(str(user_id), {})
            last = float(userdata.get("last_clone_at", 0.0))
            remaining = COOLDOWN_SECONDS - (_now() - last)
            return max(0, int(remaining))

    def record_clone(self, user_id: int, item: dict):
        """Records one finished clone: quota, cooldown, history, stats."""
        with self._lock:
            userdata = self._users.get(str(user_id))
            if userdata is not None:
                today = _today()
                if userdata.get("quota_date") != today:
                    userdata["quota_date"] = today
                    userdata["quota_used"] = 0
                userdata["quota_used"] = int(userdata.get("quota_used", 0)) + 1
                userdata["last_clone_at"] = _now()
                history = userdata.get("history", [])
                history.insert(0, item)
                del history[HISTORY_LIMIT:]
                userdata["history"] = history
                self._save(USER_FILE, self._users)
            self._stats["total_clones"] = int(self._stats.get("total_clones", 0)) + 1
            self._stats["chars_processed"] = int(self._stats.get("chars_processed", 0)) + int(item.get("chars", 0))
            self._save(STATS_FILE, self._stats)

    def history(self, user_id: int) -> list:
        with self._lock:
            return list(self.user(user_id).get("history", []))

    # ---- global settings ----------------------------------------------------
    def is_allowed(self) -> bool:
        with self._lock:
            return bool(self._settings.get("allowed", True))

    def set_allowed(self, allowed: bool):
        with self._lock:
            self._settings["allowed"] = bool(allowed)
            self._save(SETTINGS_FILE, self._settings)

    def forced_channels(self) -> list:
        with self._lock:
            return list(self._settings.get("forced_channels", []))

    def add_channel(self, channel) -> bool:
        with self._lock:
            channels = self._settings.setdefault("forced_channels", [])
            if str(channel) not in channels:
                channels.append(str(channel))
                self._save(SETTINGS_FILE, self._settings)
                return True
            return False

    def remove_channel(self, channel) -> bool:
        with self._lock:
            channels = self._settings.setdefault("forced_channels", [])
            channel = str(channel)
            if channel in channels:
                channels.remove(channel)
                self._save(SETTINGS_FILE, self._settings)
                return True
            return False

    # ---- stats --------------------------------------------------------------
    def stats(self) -> dict:
        with self._lock:
            data = dict(self._stats)
            data["users"] = self.users_count()
            return data
