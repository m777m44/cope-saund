# Clone Server (Cope Saund Mahdi)

خدمة FastAPI خفيفة توفّر واجهة REST للاستنساخ الصوتي الحقيقي (zero-shot) باستخدام
**Chatterbox Multilingual** (Resemble AI — رخصة MIT)، مع خيار محرك **SoproTTS**
(Apache-2.0، أخف ويكفي للإنجليزية) عبر المتغير `COPE_SAUND_ENGINE=sopro`،
ومحرك **ice-012-audio** (darkps — 590 لغة ولهجة من بينها 13 لهجة عربية،
مع تحكم بالأسلوب: عمر/طبقة/لهجة/همس) عبر `COPE_SAUND_ENGINE=ice`.

> ⚠️ محرك `ice` يعتمد نموذجًا بترخيص **CC-BY-NC-4.0 (غير تجاري فقط)** — راجع NOTICES.md.

## Endpoints

| Method | Path      | Body                                              | Response                       |
|--------|-----------|---------------------------------------------------|--------------------------------|
| GET    | `/health` | —                                                 | `{status, engine, languages}`  |
| POST   | `/clone`  | multipart: `ref` (أي صيغة صوتية), `text`, `language`, `speed`, `exaggeration`, `instruct` (اختياري) | `audio/wav` |
| POST   | `/enhance`| `{"text": "..."}` — تحسين النص قبل القراءة (يتطلب `ICE_AI_ENABLE=1`) | `{text}` |

## المحركات

| `COPE_SAUND_ENGINE` | النموذج | الترخيص | ملاحظات |
|---|---|---|---|
| `chatterbox` (افتراضي) | Chatterbox Multilingual | MIT | 23 لغة، تحكم عاطفة، علامة مائية |
| `sopro` | SoproTTS | Apache-2.0 | إنجليزية فقط، أخف على CPU |
| `ice` | darkps/ice-012-audio | **CC-BY-NC-4.0** | 590 لغة/لهجة، `instruct` للأسلوب، يحتاج RAM أكبر |

الصيغ المدعومة للعينة المرجعية: WAV, MP3, FLAC, OGG, M4A, AMR, 3GP — تُحوَّل تلقائيًا
إلى WAV عبر ffmpeg عند توفره.

### متغيرات البيئة الإضافية

| المتغير | الافتراضي | الوصف |
|---|---|---|
| `COPE_SAUND_ENGINE` | `chatterbox` | `chatterbox` \| `sopro` \| `ice` |
| `ICE_AI_ENABLE` | `0` | يفعّل محسّن النص ice-AI (نموذج 8B ثقيل — Apache-2.0) |

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
