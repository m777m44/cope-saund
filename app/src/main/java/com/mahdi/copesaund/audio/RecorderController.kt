package com.mahdi.copesaund.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

class RecorderController(private val context: Context) {

    @Volatile var lastLevel: Int = 0
        private set

    @Volatile private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private val stream = ByteArrayOutputStream()

    val isRecording: Boolean get() = running

    fun start(): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 4096)
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        audioRecord = record
        synchronized(stream) { stream.reset() }
        lastLevel = 0
        running = true
        worker = Thread {
            val buf = ShortArray(2048)
            try {
                record.startRecording()
            } catch (_: Exception) {
                running = false
                record.release()
                return@Thread
            }
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) break
                var sum = 0.0
                synchronized(stream) {
                    for (i in 0 until n) {
                        val v = buf[i].toInt()
                        stream.write(v and 0xFF)
                        stream.write((v shr 8) and 0xFF)
                    }
                }
                for (i in 0 until n) {
                    val s = buf[i].toDouble()
                    sum += s * s
                }
                lastLevel = (sqrt(sum / n) / 327.68).toInt().coerceIn(0, 100)
            }
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }.apply { start() }
        return true
    }

    fun stop(target: File): File {
        running = false
        worker?.join(2500)
        worker = null
        audioRecord = null
        target.parentFile?.mkdirs()
        synchronized(stream) {
            WavUtil.writeWav(target, stream.toByteArray(), SAMPLE_RATE)
        }
        return target
    }

    fun cancel() {
        running = false
        worker?.join(2500)
        worker = null
        audioRecord = null
    }

    fun recordingsDir(): File = File(context.cacheDir, "recordings").apply { mkdirs() }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 30
    }
}
