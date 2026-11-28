package com.mahdi.copesaund.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File

/** Result of probing an audio file: detected container/format and stream analysis. */
data class AudioInfo(
    val mime: String,
    val label: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
) {
    val isLossless: Boolean get() = label.contains("WAV") || label.contains("FLAC")
    val isCompressed: Boolean get() = !isLossless
}

/**
 * Automatic audio file-type detection and analysis.
 *
 * Layer 1: magic-byte sniffing (container detection independent of extension).
 * Layer 2: MediaExtractor / MediaFormat for real stream properties
 *          (mime, duration, sample rate, channel count) for MP3, M4A, FLAC,
 *          OGG, WAV, AMR, 3GP and anything the platform can demux.
 */
object AudioProbe {

    /** Detects the container format from the file header (magic bytes). */
    fun sniff(header: ByteArray): String? {
        if (header.size < 12) return null
        val ascii = { offset: Int, count: Int ->
            header.drop(offset).take(count).map { it.toInt().toChar() }.joinToString("")
        }
        return when {
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> "WAV (PCM)"
            ascii(0, 3) == "ID3" -> "MP3 (ID3)"
            (header[0].toInt() and 0xFF) == 0xFF &&
                (header[1].toInt() and 0xE0) == 0xE0 -> "MP3"
            ascii(0, 4) == "fLaC" -> "FLAC"
            ascii(0, 4) == "OggS" -> "OGG (Vorbis/Opus)"
            ascii(4, 4) == "ftyp" -> "M4A/MP4/3GP (ISO-BMFF)"
            ascii(0, 5) == "#!AMR" -> "AMR"
            ascii(0, 4) == "\u0000\u0000\u0000 " -> "MP4/3GP"
            else -> null
        }
    }

    /** Full analysis of a local file. Returns null when nothing could be demuxed. */
    fun probeFile(file: File): AudioInfo? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            analyzeTrack(extractor, file)
        } finally {
            extractor.release()
        }
    }.getOrNull()

    /** Copies the picked Uri and probes it in one pass. Returns (file, info). */
    fun probeUri(context: Context, uri: Uri, dest: File): Pair<File, AudioInfo?> {
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
            dest
        } ?: error("Could not open $uri")
        return copied to probeFile(copied)
    }

    private fun analyzeTrack(extractor: MediaExtractor, file: File): AudioInfo? {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                fmt.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            return AudioInfo(
                mime = mime,
                label = prettyLabel(mime, file, extractor, i),
                durationMs = durationUs / 1000,
                sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 0,
                channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 0,
            )
        }
        return null
    }

    private fun prettyLabel(mime: String, file: File, extractor: MediaExtractor, track: Int): String {
        val sniffed = runCatching {
            file.inputStream().use { s ->
                val head = ByteArray(16)
                var read = 0
                while (read < head.size) {
                    val n = s.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                sniff(head)
            }
        }.getOrNull()
        return when {
            sniffed != null -> sniffed
            mime.contains("mpeg") -> "MP3"
            mime.contains("mp4") || mime.contains("m4a") -> "M4A/AAC"
            mime.contains("flac") -> "FLAC"
            mime.contains("ogg") || mime.contains("opus") || mime.contains("vorbis") -> "OGG"
            mime.contains("amr") -> "AMR"
            mime.contains("raw") || mime.contains("pcm") -> "WAV (PCM)"
            mime.contains("3gpp") -> "3GP/AMR"
            else -> mime.removePrefix("audio/").uppercase()
        }
    }

    /** Human-readable one-line summary, e.g. "MP3 • 12.4s • 44.1kHz • stereo". */
    fun summarize(info: AudioInfo): String {
        val seconds = info.durationMs / 1000.0
        val dur = if (seconds >= 1.0) "%.1fs".format(seconds) else "${info.durationMs}ms"
        val rate = if (info.sampleRate > 0) " • ${info.sampleRate / 1000.0}kHz".replace(".0k", "k") else ""
        val ch = when (info.channels) {
            1 -> " • mono"
            2 -> " • stereo"
            else -> ""
        }
        return "${info.label} • $dur$rate$ch"
    }
}
