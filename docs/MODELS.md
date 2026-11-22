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

¹ ترخيص أوزان XTTS يقيد الاستخدام التجاري؛ كود Coqui تحت MPL-2.0 — غير مثالي للدمج.

## الحكم

- **أفضل جودة استنساخ متعدد اللغات منفّذة فعلياً بشكل ثابت**: Chatterbox Multilingual (MIT) — يشمل العربية، بتحكم شدة العاطفة، وعلامة مائية للحماية. ⇒ **محرك الخادم الافتراضي**.
- **أخف استنساخ إنجليزية CPU**: SoproTTS v1.5 (Apache-2.0، 135M، RTF 0.05) — بديل خفيف للخادم، ومرشّح واعد للتشغيل على الجهاز لاحقاً عندما تنضج صادرات ONNX.
- **لا يوجد اليوم نموذج استنساخ صفري يعمل بجودة مقبولة داخل هواتف أندرويد** بأوزان مفتوحة قابلة للتغليف عبر ONNX Runtime؛ لذلك اختيرت بنية هجينة: تشغيل offline عبر محرك النظام + استنساخ حقيقي عبر خادم خفيف.

## المراجع

- https://github.com/resemble-ai/chatterbox (MIT, Chatterbox Multilingual · Turbo 350M · Nano 110M)
- https://github.com/samuel-vitorino/sopro و https://huggingface.co/samuel-vitorino/sopro (SoproTTS v1.5, 135M, Apache-2.0)
- https://github.com/0xSojalSec/free-voice-clone (قائمة منسقة للنماذج وتراخيصها)
- https://github.com/myshell-ai/OpenVoice (MIT)
- https://github.com/FunAudioLLM/CosyVoice (Apache-2.0 كود)
- https://huggingface.co/hexgrad/Kokoro-82M (Apache-2.0)
- https://github.com/KittenML/KittenTTS
