package com.mahdi.copesaund.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class DeviceTts(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    suspend fun synthesize(
        text: String,
        language: String,
        speed: Float,
        pitch: Float,
        out: File
    ): Result<File> {
        val engine = tts ?: return Result.failure(IllegalStateException("TTS unavailable"))
        if (!ready) return Result.failure(IllegalStateException("TTS not ready"))
        if (language != "auto") {
            val locale = when (language) {
                "zh" -> Locale.SIMPLIFIED_CHINESE
                else -> Locale(language)
            }
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2f))
        engine.setPitch(pitch.coerceIn(0.5f, 2f))
        out.parentFile?.mkdirs()
        return withTimeout(SYNTH_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val utterance = "cope_saund_${System.currentTimeMillis()}"
                val params = Bundle()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == utterance && out.exists()) {
                            cont.resume(Result.success(out))
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == utterance) {
                            cont.resume(Result.failure(IllegalStateException("TTS synthesis failed")))
                        }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == utterance) {
                            cont.resume(Result.failure(IllegalStateException("TTS error $errorCode")))
                        }
                    }
                })
                val id = engine.synthesizeToFile(text, params, out, utterance)
                if (id != TextToSpeech.SUCCESS) {
                    cont.resume(Result.failure(IllegalStateException("TTS rejected the request")))
                }
            }
        }
    }

    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    companion object {
        private const val SYNTH_TIMEOUT_MS = 5L * 60 * 1000
    }
}
