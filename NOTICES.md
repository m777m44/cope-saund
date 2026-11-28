# Third-Party Notices

| Component | Origin | License | Notes |
|-----------|--------|---------|-------|
| OpenAI Whisper | whisper-main/ (https://github.com/openai/whisper) | MIT | Speech-to-text; bundled as uploaded by repo owner |
| Chatterbox TTS | server/ (https://github.com/resemble-ai/chatterbox) | MIT | Multilingual zero-shot voice cloning; outputs carry PerTh watermark |
| SoproTTS | server/ (https://github.com/samuel-vitorino/sopro) | Apache-2.0 | Optional lightweight English engine |
| ice-012-audio | server/ engine "ice" (https://huggingface.co/darkps/ice-012-audio) | **CC-BY-NC-4.0 (non-commercial only!)** | Voice cloning engine by DarkPs (FanuonAI); 590 languages; weights are NOT bundled — downloaded at runtime. This project's MIT license does NOT apply to these weights: any commercial use of this engine requires a separate license from the rights holder. |
| ice-AI (ICE 0001) | server/ optional text enhancer (https://huggingface.co/darkps/ice-AI) | Apache-2.0 | Qwen3-8B text model by DarkPs (FanuonAI); used only via optional `/enhance` endpoint (ICE_AI_ENABLE=1) |
| Jetpack Compose / AndroidX | androidx.dev | Apache-2.0 | UI framework |
| OkHttp | https://github.com/square/okhttp | Apache-2.0 | HTTP client |
