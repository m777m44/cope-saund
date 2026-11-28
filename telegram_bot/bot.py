"""Cope Saund Mahdi - Telegram bot for the self-hosted voice-clone server.

All secrets come from environment variables (see config.py).
Run: BOT_TOKEN=... ADMIN_ID=... CLONE_SERVER_URL=http://localhost:8000 python bot.py
"""
import logging
import os
import threading
import time
from datetime import datetime, timezone

from telebot import TeleBot, types

import config
from clone_client import CloneClient, CloneError
from storage import Storage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
log = logging.getLogger("cope-saund-bot")

os.makedirs(config.SAMPLES_DIR, exist_ok=True)

BOT = TeleBot(config.BOT_TOKEN, parse_mode="HTML")
storage = Storage(config.DATA_DIR)
client = CloneClient(config.CLONE_SERVER_URL)

STEP_SAMPLE = "sample"
STEP_TEXT = "text"
STEP_LANG = "lang"

_steps: dict = {}
_steps_lock = threading.Lock()
_admin_state: dict = {}


def _set_step(user_id: int, **kv):
    with _steps_lock:
        state = _steps.setdefault(user_id, {})
        if kv.get("reset"):
            _steps.pop(user_id, None)
            return
        state.update(kv)


def _get_step(user_id: int, key: str, default=None):
    with _steps_lock:
        return _steps.get(user_id, {}).get(key, default)


# ---------------------------------------------------------------- keyboards
def main_menu(user_id: int) -> types.InlineKeyboardMarkup:
    markup = types.InlineKeyboardMarkup(row_width=2)
    markup.add(
        types.InlineKeyboardButton("\U0001F3A4 استنساخ صوت جديد", callback_data="new"),
        types.InlineKeyboardButton("\U0001F5D2 السجل", callback_data="history"),
    )
    markup.add(types.InlineKeyboardButton("\u2764 المساعدة", callback_data="help"))
    if user_id == config.ADMIN_ID:
        markup.add(types.InlineKeyboardButton("\U0001F6E0 لوحة الإدارة", callback_data="admin"))
    return markup


def language_keyboard() -> types.InlineKeyboardMarkup:
    markup = types.InlineKeyboardMarkup(row_width=2)
    buttons = [
        types.InlineKeyboardButton(name, callback_data=f"lang:{code}")
        for code, name in config.SUPPORTED_LANGUAGES
    ]
    markup.add(*buttons)
    return markup


def admin_keyboard() -> types.InlineKeyboardMarkup:
    markup = types.InlineKeyboardMarkup(row_width=2)
    markup.add(
        types.InlineKeyboardButton("\U0001F4CA الإحصائيات", callback_data="admin:stats"),
        types.InlineKeyboardButton("\U0001F3E0 إيقاف/تشغيل الخدمة", callback_data="admin:toggle"),
    )
    markup.add(
        types.InlineKeyboardButton("\U0001F514 إشاعة عامة", callback_data="admin:broadcast"),
        types.InlineKeyboardButton("💎 عضوية مدفوعة", callback_data="admin:paid"),
    )
    markup.add(
        types.InlineKeyboardButton("\U0001F4E2 القنوات الإجبارية", callback_data="admin:channels"),
        types.InlineKeyboardButton("\U0001F4C8 حد يومي (مجاني)", callback_data="admin:limit"),
    )
    return markup


# ------------------------------------------------------------ subscription
def subscription_ok(user_id: int, chat_id: int) -> bool:
    channels = storage.forced_channels()
    if not channels:
        return True
    pending = []
    for channel in channels:
        try:
            member = BOT.get_chat_member(channel, user_id)
            if member.status in ("left", "kicked"):
                pending.append(channel)
        except Exception as exc:
            log.warning("Subscription check failed for %s: %s", channel, exc)
            pending.append(channel)
    if pending:
        buttons = [types.InlineKeyboardButton("قناة " + ch, url=f"https://t.me/{ch.lstrip('@')}") for ch in pending if ch.startswith("@")]
        markup = types.InlineKeyboardMarkup()
        for b in buttons:
            markup.add(b)
        markup.add(types.InlineKeyboardButton("✅ تحقق", callback_data="check_sub"))
        BOT.send_message(chat_id, "🔔 يجب الاشتراك في القنوات التالية أولًا:", reply_markup=markup)
        return False
    return True


# ------------------------------------------------------------- UI helpers
def welcome_text(user) -> str:
    _, remaining, limit = storage.quota_check(user.id)
    return (
        "<b>🎭 مرحبًا بك في بوت استنساخ الأصوات — Cope Saund Mahdi</b>\n\n"
        "أرسل <b>مقطعًا صوتيًا</b> من شخص واحد ("
        f"{config.MIN_SAMPLE_SECONDS}-{config.MAX_SAMPLE_SECONDS} ثانية) ثم اكتب النص المراد نطقه بصوته.\n\n"
        f"🎬 الحصة اليومية المتبقية: <b>{remaining}</b> / {limit}"
    )


# ---------------------------------------------------------------- commands
@BOT.message_handler(commands=["start"])
def start(message: types.Message):
    storage.ensure_user(message.from_user.id, message.from_user.first_name or "", message.from_user.username or "")
    BOT.send_message(message.chat.id, welcome_text(message.from_user), reply_markup=main_menu(message.from_user.id))


@BOT.message_handler(commands=["help"])
def help_cmd(message: types.Message):
    BOT.send_message(
        message.chat.id,
        "<b>ℹ كيف تستخدم البوت:</b>\n\n"
        "1. أرسل <b>مقطعًا صوتيًا</b> (voice) من شخص واحد واضح — "
        f"{config.MIN_SAMPLE_SECONDS} إلى {config.MAX_SAMPLE_SECONDS} ثانية.\n"
        "2. اختر لغة النص من القائمة.\n"
        "3. اكتب النص المراد نطقه بصوته المقلد.\n"
        "4. سترجع النتيجة كملف صوتي.\n\n"
        "<b>ملاحظة:</b> إرسال عينة جديدة يبدأ عملية استنساخ جديدة.\n"
        f"/history — أحدث {config.HISTORY_LIMIT} عمليات\n"
        "/cancel — إلغاء العملية الحالية\n"
        "/stats — إحصائياتك",
    )


@BOT.message_handler(commands=["cancel"])
def cancel_cmd(message: types.Message):
    _set_step(message.from_user.id, reset=True)
    BOT.send_message(message.chat.id, "✋ تم إلغاء العملية الحالية.", reply_markup=main_menu(message.from_user.id))


@BOT.message_handler(commands=["history"])
def history_cmd(message: types.Message):
    items = storage.history(message.from_user.id)
    if not items:
        BOT.send_message(message.chat.id, "\U0001F4C4 لا يوجد سجل بعد.")
        return
    lines = ["\U0001F5D2 <b>آخر عملياتك:</b>\n"]
    for index, item in enumerate(items, start=1):
        preview = (item.get("text", "") or "")[:60]
        when = datetime.fromtimestamp(item.get("ts", 0.0), timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        lines.append(f"{index}. <i>{preview}…</i>\n   {item.get('language', '?')} • {when}\n")
    BOT.send_message(message.chat.id, "\n".join(lines))


@BOT.message_handler(commands=["stats"])
def stats_cmd(message: types.Message):
    userdata = storage.user(message.from_user.id)
    quota_ok, remaining, limit = storage.quota_check(message.from_user.id)
    cooldown = storage.cooldown_remaining(message.from_user.id)
    paid = "💎 مدفوعة" if userdata.get("is_paid") else "مجانية"
    BOT.send_message(
        message.chat.id,
        f"<b>📊 إحصائياتك</b>\n\n"
        f"العضوية: <b>{paid}</b>\n"
        f"الحصة اليومية المتبقية: <b>{remaining}</b> / {limit}\n"
        f"وقت التهدئة المتبقي: <b>{cooldown}</b> ثانية\n"
        f"إجمالي عملياتك المسجلة في السجل: <b>{len(storage.history(message.from_user.id))}</b>",
    )


# -------------------------------------------------------- audio/voice input
@BOT.message_handler(content_types=["audio", "voice"])
def handle_audio(message: types.Message):
    if not storage.is_allowed():
        BOT.reply_to(message, "⚠️ الخدمة موقوفة مؤقتًا من الإدارة.")
        return
    if not subscription_ok(message.from_user.id, message.chat.id):
        return

    duration = getattr(message.voice, "duration", None) or getattr(message.audio, "duration", 0) or 0
    if duration < config.MIN_SAMPLE_SECONDS or duration > config.MAX_SAMPLE_SECONDS + 3:
        BOT.reply_to(
            message,
            f"⏱ مدة العينة يجب أن تكون بين <b>{config.MIN_SAMPLE_SECONDS}</b> و <b>{config.MAX_SAMPLE_SECONDS}</b> ثانية. (استقبلت {duration}s)",
        )
        return

    file_id = message.voice.file_id if message.voice else message.audio.file_id
    try:
        info = BOT.get_file(file_id)
        data = BOT.download_file(info.file_path)
    except Exception as exc:
        log.warning("Download failed: %s", exc)
        BOT.reply_to(message, "❌ فشل تحميل الملف الصوتي، حاول مرة أخرى.")
        return

    ext = os.path.splitext(info.file_path or "sample.ogg")[1] or ".ogg"
    os.makedirs(config.SAMPLES_DIR, exist_ok=True)
    path = os.path.join(config.SAMPLES_DIR, f"{message.from_user.id}_{int(time.time())}{ext}")
    with open(path, "wb") as fh:
        fh.write(data)

    _set_step(message.from_user.id, step=STEP_LANG, sample=path)
    BOT.reply_to(
        message,
        f"✅ تم استلام العينة ({duration}s).\nالآن اختر <b>لغة النص</b> الذي سأقرؤه بصوته:",
        reply_markup=language_keyboard(),
    )


# ---------------------------------------------------------------- text input
@BOT.message_handler(content_types=["text"], func=lambda m: True)
def handle_text(message: types.Message):
    if message.from_user.id == config.ADMIN_ID and _admin_state.get("action"):
        return _handle_admin_input(message)

    step = _get_step(message.from_user.id, "step")
    if not step:
        storage.ensure_user(message.from_user.id)
        if message.text.startswith("/"):
            BOT.send_message(message.chat.id, "أرسل /start للبدء.")
        else:
            BOT.send_message(message.chat.id, "🎤 أرسل عينة صوتية أولًا (مقطع voice من 10-30 ثانية).", reply_markup=main_menu(message.from_user.id))
        return

    if step != STEP_TEXT:
        return

    text = (message.text or "").strip()
    if len(text) < 3:
        BOT.reply_to(message, "✍ النص قصير جدًا، اكتب نصًا أوضح.")
        return
    if len(text) > config.MAX_TEXT_LENGTH:
        BOT.reply_to(message, f"✍ النص طويل جدًا ({len(text)}). الحد الأقصى {config.MAX_TEXT_LENGTH} حرفًا.")
        return

    cooldown = storage.cooldown_remaining(message.from_user.id)
    if cooldown > 0:
        BOT.reply_to(message, f"⏳ انتظر <b>{cooldown}</b> ثانية بين كل عملية استنساخ.")
        return
    quota_ok, remaining, limit = storage.quota_check(message.from_user.id)
    if not quota_ok:
        BOT.reply_to(message, f"🚫 انتهت حصتك اليومية ({limit}). جرّب غدًا أو اطلب عضوية مدفوعة.")
        return

    sample = _get_step(message.from_user.id, "sample")
    language = _get_step(message.from_user.id, "language", "auto")
    if not sample or not os.path.isfile(sample):
        BOT.reply_to(message, "⚠️ انتهت صلاحية العينة، أرسلها مرة أخرى.")
        return

    status = BOT.reply_to(message, "⏳ جاري توليد الصوت باستنساخ صوتك... قد يستغرق حتى دقيقة.")
    try:
        wav_bytes = client.clone(sample, text, language=language)
    except CloneError as exc:
        log.warning("Clone failed for %s: %s", message.from_user.id, exc)
        BOT.edit_message_text(f"❌ فشل الاستنساخ:\n<code>{exc}</code>", message.chat.id, status.message_id)
        return

    result_path = os.path.join(config.SAMPLES_DIR, f"result_{message.from_user.id}_{int(time.time())}.wav")
    with open(result_path, "wb") as fh:
        fh.write(wav_bytes)

    try:
        with open(result_path, "rb") as fh:
            BOT.send_audio(
                message.chat.id,
                fh,
                caption=f"🎭 <b>النص:</b> {text[:180]}\n🗣 اللغة: {language}",
                reply_to_message_id=message.id,
            )
    except Exception as exc:
        log.warning("Send audio failed: %s", exc)
        BOT.edit_message_text("❌ فشل إرسال الملف الصوتي.", message.chat.id, status.message_id)
        return
    finally:
        try:
            os.remove(result_path)
        except OSError:
            pass

    BOT.delete_message(message.chat.id, status.message_id)
    storage.record_clone(
        message.from_user.id,
        {
            "text": text[:120],
            "language": language,
            "chars": len(text),
            "ts": time.time(),
        },
    )
    _set_step(message.from_user.id, step=STEP_TEXT)
    BOT.send_message(message.chat.id, "✅ تم! أرسل نصًا آخر للتوليد مجددًا، أو عينة صوتية جديدة.")


# ----------------------------------------------------------------- callbacks
@BOT.callback_query_handler(func=lambda call: True)
def callback(call: types.CallbackQuery):
    user_id = call.from_user.id
    data = call.data or ""

    if data == "check_sub":
        if subscription_ok(user_id, call.message.chat.id):
            BOT.edit_message_text("✅ تم التحقق من الاشتراك، يمكنك المتابعة.", call.message.chat.id, call.message.id, reply_markup=main_menu(user_id))
        else:
            BOT.answer_callback_query(call.id, "لم يكتمل الاشتراك بعد.")
        BOT.answer_callback_query(call.id)
        return

    if data == "admin" and user_id == config.ADMIN_ID:
        BOT.edit_message_text("\U0001F6E0 <b>لوحة الإدارة</b>", call.message.chat.id, call.message.id, reply_markup=admin_keyboard())
        BOT.answer_callback_query(call.id)
        return

    if data.startswith("admin:") and user_id == config.ADMIN_ID:
        return _handle_admin_callback(call)

    if data == "new":
        _set_step(user_id, step=STEP_SAMPLE)
        BOT.edit_message_text(
            f"🎤 أرسل الآن مقطعًا صوتيًا من {config.MIN_SAMPLE_SECONDS} إلى {config.MAX_SAMPLE_SECONDS} ثانية.",
            call.message.chat.id, call.message.id,
        )
        BOT.answer_callback_query(call.id)
        return

    if data == "history":
        items = storage.history(user_id)
        text = "\n".join(
            f"{index}. {(item.get('text', '') or '')[:50]}…" for index, item in enumerate(items, start=1)
        ) or "لا يوجد سجل بعد."
        BOT.edit_message_text(text, call.message.chat.id, call.message.id)
        BOT.answer_callback_query(call.id)
        return

    if data == "help":
        BOT.edit_message_text(
            "ℹ أرسل مقطعًا صوتيًا (voice) من 10-30 ثانية، ثم اختر اللغة، ثم اكتب النص.",
            call.message.chat.id, call.message.id,
        )
        BOT.answer_callback_query(call.id)
        return

    if data.startswith("lang:"):
        language = data.split(":", 1)[1]
        _set_step(user_id, step=STEP_TEXT, language=language)
        BOT.edit_message_text(
            f"🗣 اللغة: <b>{language}</b>\n✍ اكتب الآن النص الذي سأنطقه بصوتك المقلد:",
            call.message.chat.id, call.message.id,
        )
        BOT.answer_callback_query(call.id)
        return

    BOT.answer_callback_query(call.id)


# -------------------------------------------------------------- admin panel
def _handle_admin_callback(call: types.CallbackQuery):
    data = call.data.split(":", 1)[1]
    chat_id, message_id = call.message.chat.id, call.message.id

    if data == "stats":
        stats = storage.stats()
        BOT.edit_message_text(
            "<b>📊 إحصائيات البوت</b>\n\n"
            f"المستخدمون: <b>{stats.get('users', 0)}</b>\n"
            f"إجمالي عمليات الاستنساخ: <b>{stats.get('total_clones', 0)}</b>\n"
            f"إجمالي الحروف المعالجة: <b>{stats.get('chars_processed', 0)}</b>",
            chat_id, message_id, reply_markup=admin_keyboard(),
        )
    elif data == "toggle":
        allowed = not storage.is_allowed()
        storage.set_allowed(allowed)
        BOT.edit_message_text(
            f"الخدمة الآن: <b>{'تعمل ✅' if allowed else 'موقوفة ⚠️'}</b>",
            chat_id, message_id, reply_markup=admin_keyboard(),
        )
    elif data == "broadcast":
        _admin_state["action"] = "broadcast"
        BOT.edit_message_text("📣 أرسل الآن الرسالة التي تريد إشاعتها لجميع المستخدمين:", chat_id, message_id)
    elif data == "paid":
        _admin_state["action"] = "paid"
        BOT.edit_message_text(
            "💎 أرسل: <code>USER_ID </code>\n"
            "لتفعيل عضوية مدفوعة للمستخدم، أو أرسل: <code>USER_ID free</code> لإلغائها.",
            chat_id, message_id,
        )
    elif data == "channels":
        channels = storage.forced_channels()
        list_text = "\n".join(channels) or "(لا توجد قنوات إجبارية)"
        _admin_state["action"] = "channel_add"
        BOT.edit_message_text(
            f"📢 القنوات الإجبارية الحالية:\n{list_text}\n\n"
            "أرسل <code>@اسم_القناة</code> لإضافة قناة، أو <code>حذف @اسم_القناة</code> لإزالتها.",
            chat_id, message_id,
        )
    elif data == "limit":
        _admin_state["action"] = "limit"
        BOT.edit_message_text(
            f"الحد اليومي المجاني الحالي: <b>{storage.quota_check(config.ADMIN_ID)[2]}</b>\nأرسل الرقم الجديد:",
            chat_id, message_id,
        )
    BOT.answer_callback_query(call.id)


def _handle_admin_input(message: types.Message):
    action = _admin_state.pop("action")
    chat_id = message.chat.id

    if action == "broadcast":
        ids = storage.all_user_ids()
        sent = 0
        note = BOT.reply_to(message, f"⏳ جارٍ الإشعار لـ {len(ids)} مستخدم...")
        for uid in ids:
            try:
                BOT.send_message(int(uid), message.text)
                sent += 1
            except Exception:
                continue
        try:
            BOT.edit_message_text(f"📣 تم الإشعار لـ {sent} مستخدم.", chat_id, note.message_id)
        except Exception:
            pass

    elif action == "paid":
        parts = (message.text or "").split()
        if not parts or not parts[0].lstrip("-").isdigit():
            BOT.reply_to(message, "❌ معرف المستخدم غير صالح.")
            return
        uid = int(parts[0])
        is_free = len(parts) > 1 and parts[1].lower() == "free"
        if storage.set_paid(uid, paid=not is_free):
            BOT.reply_to(message, f"✅ تم تحديث عضوية <code>{uid}</code>: {'مجانية' if is_free else 'مدفوعة'}.")
        else:
            BOT.reply_to(message, "❌ المستخدم غير موجود في قاعدة البيانات (يجب أن يكون قد أرسل /start).")

    elif action == "channel_add":
        text = (message.text or "").strip()
        if text.lower().startswith("حذف "):
            channel = text.split(None, 1)[1].strip()
            ok = storage.remove_channel(channel)
            BOT.reply_to(message, "✅ تمت الإزالة." if ok else "⚠️ القناة غير موجودة.")
        else:
            if not text.startswith("@") and not text.lstrip("-").isdigit():
                BOT.reply_to(message, "❌ أدخل معرفًا يبدأ بـ @ أو معرف رقمي.")
                return
            storage.add_channel(text)
            BOT.reply_to(message, f"✅ تمت إضافة القناة: {text}")

    elif action == "limit":
        if not (message.text or "").strip().isdigit():
            BOT.reply_to(message, "❌ أدخل رقمًا صحيحًا.")
            return
        storage.set_daily_limit(int(message.text.strip()))
        BOT.reply_to(message, f"✅ الحد اليومي المجاني الآن: {message.text.strip()}")


# ------------------------------------------------------------------- main
def main():
    errors = config.validate()
    if errors:
        for err in errors:
            log.error("CONFIG ERROR: %s", err)
        raise SystemExit(1)

    try:
        health = client.health()
        log.info("Clone server OK: engine=%s languages=%s", health.get("engine"), health.get("languages"))
    except CloneError as exc:
        log.warning("Clone server not reachable at startup: %s", exc)

    log.info("Bot started (admin=%s, server=%s)", config.ADMIN_ID, config.CLONE_SERVER_URL)
    BOT.infinity_polling(timeout=30, long_polling_timeout=20, skip_pending=True)


if __name__ == "__main__":
    main()
