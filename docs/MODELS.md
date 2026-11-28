# مقارنة نماذج استنساخ الأصوات مفتوحة المصدر (بحث 2025–2026)

مصادر البحث: GitHub, Hugging Face, Reddit (r/LocalLLaMA, r/MachineLearning), وقوائم منسقة
مثل 0xSojalSec/free-voice-clone و awesome-ai-voice.

## الجدول المقارن

| النموذج | الترخيص | المعاملات | الاستنساخ | اللغات | الأداء (CPU/ARM) | قابلية أندرويد على الجهاز |
|---------|---------|-----------|-----------|--------|------------------|---------------------------|
| Coqui XTTS v2 | GPL/CPML-NonFan مقيّد¹ | ~750M | zero-shot من 3–6 ثوانٍ | 17 | بطيء جداً على CPU | ❌ غير عملي |
| OpenVoice v2 | MIT | ~100M + مقسم نبرة | نقل نبرة (tone-color) | متعدد | متوسط | ⚠️ لا توجد حزمة ONNX رسمية موثوقة |
| SoproTTS v1.5 | Apache-2.0 | 135M | zero-shot (3–12 ث) | إنجليزية | RTF ≈ 0.05 (~20× زمن حقيقي) | ⚠️ مرشّح للجهاز مستقبلاً عبر ONNX |
| Chatterbox (Multilingual) | MIT (كود + أوزان) | 0.5B–1B | zero-shot فوري + تحكم عاطفة | 23 (تشمل العربية) | يحتاج ~4–8GB RAM | ❌ للجهاز، ✅ خادم |
| CosyVoice 2 | Apache-2.0 (كود)، أوزان غير تجارية | ~2B تقريباً | zero-shot متعدد اللغات | متعدد | ثقيل | ❌ |
| Kokoro 0.8 | Apache-2.0 | 82M | ❌ أصوات ثابتة فقط | متعدد | خفيف جداً (ONNX ممتاز) | ✅ يعمل — لكن لا استنساخ |
| KittenTTS Nano | Apache-2.0 | 15M (~25MB) | ❌ أصوات مضمنة | en | خفيف جداً | ✅ يعمل — لكن لا استنساخ |
| ice-012-audio (darkps) | **CC-BY-NC-4.0 ⚠ غير تجاري فقط** | 0.7B | من عينة مرجعية + تحكم أسلوب (عمر/طبقة/لهجة/همس) | **590 لغة ولهجة** (13 لهجة عربية) | ثقيل (يحتاج ~4GB+ RAM) | ❌ خادم فقط |
| ice-AI / ICE 0001 (darkps) | Apache-2.0 | 8B (نصّي — ليس TTS) | — (تحسين نص قبل القراءة) | 119 لغة | 8B يتطلب ~8GB+ RAM | ❌ خادم اختياري |

¹ ترخيص أوزان XTTS يقيد الاستخدام التجاري؛ كود Coqui تحت MPL-2.0 — غير مثالي للدمج.

### ice-012-audio (darkps / FanuonAI)

نموذج TTS واستنساخ صوتي مفتوح على Hugging Face (0.7B، كود مخصص `trust_remote_code`)، يدعم **590 لغة ولهجة** من بينها 13 لهجة عربية، مع تحكم بالأسلوب (gender · age · pitch · accent · style) واستنساخ من عينة مرجعية.

> ⚠️ **ترخيص CC-BY-NC-4.0**: يسمح بالاستخدام **غير التجاري فقط** مع نسب الملكية. لا يجوز دمجه في منتج تجاري دون ترخيص من صاحبه. لهذا لم تُدمج أوزانه في هذا المستودع ولا تُغطّى بترخيص MIT الخاص به؛ يُنزَّل وقت التشغيل كمحرك اختياري (`COPE_SAUND_ENGINE=ice`) — راجع NOTICES.md.

### ice-AI (darkps / FanuonAI)

نموذج محادثة نصية (Qwen3-8B، Apache-2.0، سياق 32K) مركّز على العربية بلهجاتها. **ليس نموذج صوت** — دوره في هذا المشروع تحسين/تنظيف النص قبل القراءة (توسيع الأرقام، علامات الترقيم) عبر نقطة `/enhance` الاختيارية (معطّلة افتراضيًا، تُشغّل بـ `ICE_AI_ENABLE=1`).

## الحكم

- **أفضل جودة استنساخ متعدد اللغات منفّذة فعلياً بشكل ثابت**: Chatterbox Multilingual (MIT) — يشمل العربية، بتحكم شدة العاطفة، وعلامة مائية للحماية. ⇒ **محرك الخادم الافتراضي**.
- **أخف استنساخ إنجليزية CPU**: SoproTTS v1.5 (Apache-2.0، 135M، RTF 0.05) — بديل خفيف للخادم، ومرشّح واعد للتشغيل على الجهاز لاحقاً عندما تنضج صادرات ONNX.
- **لا يوجد اليوم نموذج استنساخ صفري يعمل بجودة مقبولة داخل هواتف أندرويد** بأوزان مفتوحة قابلة للتغليف عبر ONNX Runtime؛ لذلك اختيرت بنية هجينة: تشغيل offline عبر محرك النظام + استنساخ حقيقي عبر خادم خفيف.

## المراجع

- https://github.com/resemble-ai/chatterbox (MIT, Chatterbox Multilingual · Turbo 350M · Nano 110M)
- https://huggingface.co/darkps/ice-012-audio (CC-BY-NC-4.0، 590 لغة، استنساخ مرجعي)
- https://huggingface.co/darkps/ice-AI (Apache-2.0، ICE 0001، 8B محادثة)
- https://github.com/samuel-vitorino/sopro و https://huggingface.co/samuel-vitorino/sopro (SoproTTS v1.5, 135M, Apache-2.0)
- https://github.com/0xSojalSec/free-voice-clone (قائمة منسقة للنماذج وتراخيصها)
- https://github.com/myshell-ai/OpenVoice (MIT)
- https://github.com/FunAudioLLM/CosyVoice (Apache-2.0 كود)
- https://huggingface.co/hexgrad/Kokoro-82M (Apache-2.0)
- https://github.com/KittenML/KittenTTS
