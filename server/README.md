# Clone Server (Cope Saund Mahdi)

خدمة FastAPI خفيفة توفّر واجهة REST للاستنساخ الصوتي الحقيقي (zero-shot) باستخدام
**Chatterbox Multilingual** (Resemble AI — رخصة MIT)، مع خيار محرك **SoproTTS**
(Apache-2.0، أخف ويكفي للإنجليزية) عبر المتغير `COPE_SAUND_ENGINE=sopro`.

## Endpoints

| Method | Path      | Body                                              | Response                       |
|--------|-----------|---------------------------------------------------|--------------------------------|
| GET    | `/health` | —                                                 | `{status, engine, languages}`  |
| POST   | `/clone`  | multipart: `ref` (ملف صوتي), `text`, `language`, `speed`, `exaggeration` | `audio/wav` |

## التشغيل عبر Docker (الموصى به)

```bash
cd server
docker build -t cope-saund-server .
docker run -p 8000:8000 -v ~/.cache/cope-saund-models:/models cope-saund-server
```

يمثّل `/models` كاش نماذج Hugging Face؛ تُنزَّل الأوزان تلقائياً عند أول استنساخ (~2GB).

## التشغيل عبر Python مباشرة

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000
```

## ربط تطبيق أندرويد

1. افتح **Cope Saund Mahdi ← الإعدادات**
2. اختر المحرك: *Server* أو *Auto*
3. أدخل العنوان: `http://<server-ip>:8000` ثم اضغط **اختبار**

> الخادم مصمَّم للاستخدام الشخصي على الشبكة المحلية. إن أردت كشفه للإنترنت
> فضع خلف وكيل HTTPS مع حماية بمفتاح (وإلا سيصبح مفتوحاً للجميع).

## الاستخدام المسؤول

- استنسخ الأصوات التي تملك الحق في استخدامها فقط.
- جميع المخرجات من Chatterbox تتضمن علامة PerTh المائية غير المسموعة من Resemble AI.
