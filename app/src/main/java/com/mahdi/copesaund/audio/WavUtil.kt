package com.mahdi.copesaund.audio

import android.media.MediaPlayer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtil {

    fun writeWav(target: File, pcm: ByteArray, sampleRate: Int, channels: Int = 1, bits: Int = 16) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val dataLen = pcm.size
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort()) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray())
        header.putInt(dataLen)
        target.outputStream().use { out ->
            out.write(header.array())
            out.write(pcm)
        }
    }

    fun durationMs(file: File): Long = try {
        val player = MediaPlayer()
        player.setDataSource(file.absolutePath)
        player.prepare()
        val d = player.duration.toLong()
        player.release()
        d
    } catch (_: Exception) {
        0L
    }

    fun readAmplitudes(file: File, buckets: Int = 96): IntArray? = try {
        RandomAccessFile(file, "r").use { raf ->
            val len = minOf(raf.length().toInt(), 16 * 1024 * 1024)
            val head = ByteArray(len)
            raf.seek(0)
            raf.readFully(head, 0, len)
            val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            if (!String(head, 0, 4).contains("RIFF")) return@use null
            var sampleRate = 16000
            var channels = 1
            var bits = 16
            var dataOff = -1
            var dataLen = 0
            var pos = 12
            while (pos + 8 <= len) {
                val id = String(head, pos, 4)
                val size = getInt(head, pos + 4)
                when (id) {
                    "fmt " -> {
                        buf.position(pos + 8)
                        buf.getShort() // audio format
                        channels = buf.getShort().toInt().coerceIn(1, 8)
                        sampleRate = getInt(head, pos + 12)
                        buf.position(pos + 20)
                        bits = buf.getShort().toInt()
                    }
                    "data" -> {
                        dataOff = pos + 8
                        dataLen = size.coerceAtMost(len - dataOff)
                    }
                }
                pos += 8 + size + (size % 2)
                if (dataOff >= 0) break
            }
            if (dataOff < 0 || bits != 16) return@use null
            val frameBytes = channels * bits / 8
            val frames = dataLen / frameBytes
            val result = IntArray(buckets)
            if (frames == 0) return@use result
            val per = maxOf(1, frames / buckets)
            for (b in 0 until buckets) {
                var peak = 0
                for (f in 0 until per) {
                    val idx = dataOff + (b * per + f) * frameBytes
                    if (idx + 1 >= len) break
                    buf.position(idx)
                    var v = buf.getShort().toInt()
                    if (v < 0) v = 0
                    if (v > peak) peak = v
                }
                result[b] = (peak * 100 / 32768).coerceIn(2, 100)
            }
            result
        }
    } catch (_: Exception) {
        null
    }

    private fun getInt(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)
}
