package com.mahdi.copesaund.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mahdi.copesaund.R
import com.mahdi.copesaund.audio.WavUtil
import com.mahdi.copesaund.model.HistoryItem
import com.mahdi.copesaund.ui.common.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlayerScreen(item: HistoryItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var amplitudes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    val player = remember { MediaPlayer() }

    LaunchedEffect(item) {
        amplitudes = withContext(Dispatchers.IO) {
            WavUtil.readAmplitudes(File(item.filePath))?.toList() ?: emptyList()
        }
    }

    LaunchedEffect(item) {
        ready = withContext(Dispatchers.IO) {
            try {
                player.setDataSource(item.filePath)
                player.prepare()
                durationMs = player.duration.toLong()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            positionMs = withContext(Dispatchers.IO) { player.currentPosition.toLong() }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
            Text(
                text = stringResource(R.string.player_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        WaveformView(amplitudes, Modifier.fillMaxWidth(), progress)
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatMs(positionMs) + " / " + formatMs(durationMs),
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = progress,
            onValueChange = {
                val newPos = (it * durationMs).toLong()
                positionMs = newPos
                player.seekTo(newPos.toInt())
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        player.start()
                        playing = true
                    }
                },
                enabled = ready
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = null
                )
                Text(stringResource(if (playing) R.string.pause else R.string.play))
            }
            Button(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { saveToMusic(context, item) }
                    status = context.getString(if (ok) R.string.saved_to else R.string.save_failed)
                }
            }) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                Text(stringResource(R.string.download))
            }
            OutlinedButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    val uri = FileProvider.getUriForFile(
                        context, context.packageName + ".fileprovider", File(item.filePath)
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(share, null))
            }) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                Text(stringResource(R.string.share))
            }
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun saveToMusic(context: Context, item: HistoryItem): Boolean {
    return try {
    val src = File(item.filePath)
    val name = "cope-saund-mahdi-${item.id.take(8)}.wav"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: return false
        true
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            ""
        ).apply { mkdirs() }
        src.copyTo(File(dir, name), overwrite = true)
        true
    }
    } catch (_: Exception) {
        false
    }
}
