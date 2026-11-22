package com.mahdi.copesaund.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.core.content.ContextCompat
import com.mahdi.copesaund.R
import com.mahdi.copesaund.audio.RecorderController
import com.mahdi.copesaund.audio.WavUtil
import com.mahdi.copesaund.data.AppSettings
import com.mahdi.copesaund.data.HistoryStore
import com.mahdi.copesaund.model.HistoryItem
import com.mahdi.copesaund.model.Languages
import com.mahdi.copesaund.network.CloneClient
import com.mahdi.copesaund.tts.DeviceTts
import com.mahdi.copesaund.ui.common.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun HomeScreen(
    settings: AppSettings,
    historyStore: HistoryStore,
    history: MutableList<HistoryItem>,
    onCreated: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recorder = remember { RecorderController(context) }
    val client = remember { CloneClient(context) }
    val deviceTts = remember { DeviceTts(context) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicPermission = it
    }

    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var levels by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sampleFile by remember { mutableStateOf<File?>(null) }
    var sampleDurationMs by remember { mutableLongStateOf(0L) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val out = File(recorder.recordingsDir(), "import_${System.currentTimeMillis()}")
                val f = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    out
                }
                val d = withContext(Dispatchers.IO) { WavUtil.durationMs(f) }
                val a = withContext(Dispatchers.IO) { WavUtil.readAmplitudes(f) }
                sampleFile = f
                sampleDurationMs = d
                levels = a?.toList() ?: emptyList()
            }
        }
    }

    var text by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("auto") }
    var languageOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    var exaggeration by remember { mutableFloatStateOf(1.25f) }
    var consent by remember { mutableStateOf(settings.consentNeverAsk) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            deviceTts.release()
        }
    }

    val stopRecording: () -> Unit = {
        if (recorder.isRecording) {
            val out = File(recorder.recordingsDir(), "sample_${System.currentTimeMillis()}.wav")
            val f = recorder.stop(out)
            recording = false
            sampleFile = f
            scope.launch {
                val d = withContext(Dispatchers.IO) { WavUtil.durationMs(f) }
                val a = withContext(Dispatchers.IO) { WavUtil.readAmplitudes(f) }
                sampleDurationMs = d
                levels = a?.toList() ?: emptyList()
            }
        }
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val started = System.currentTimeMillis()
        while (recorder.isRecording && System.currentTimeMillis() - started < RecorderController.MAX_SECONDS * 1000L) {
            elapsedMs = System.currentTimeMillis() - started
            levels = levels + recorder.lastLevel
            delay(80)
        }
        if (recorder.isRecording) stopRecording()
    }

    val startClone: () -> Unit = {
        when {
            sampleFile == null -> error = context.getString(R.string.err_no_sample)
            text.isBlank() -> error = context.getString(R.string.err_no_text)
            !consent -> error = context.getString(R.string.err_no_consent)
            else -> {
                busy = true
                error = null
                val sample = sampleFile!!
                val useServer = settings.serverEffective
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (useServer) {
                            client.clone(settings.serverUrl, text.trim(), language, speed, exaggeration, sample)
                        } else {
                            val dir = File(context.filesDir, "exports").apply { mkdirs() }
                            val out = File(dir, "clone_${System.currentTimeMillis()}.wav")
                            deviceTts.synthesize(text.trim(), language, speed, pitch, out)
                        }
                    }
                    busy = false
                    result.fold(
                        onSuccess = { out ->
                            val dur = withContext(Dispatchers.IO) { WavUtil.durationMs(out) }
                            val item = HistoryItem(
                                id = UUID.randomUUID().toString(),
                                text = text.trim(),
                                language = language,
                                engine = if (useServer) "server" else "device",
                                createdAt = System.currentTimeMillis(),
                                filePath = out.absolutePath,
                                durationMs = dur
                            )
                            historyStore.add(history, item)
                            onCreated(item)
                        },
                        onFailure = { e ->
                            error = (e.message ?: "Error") +
                                if (useServer) "\n" + context.getString(R.string.err_no_server) else ""
                        }
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.sample_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.sample_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recording) {
                    WaveformView(levels, Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.recording_time, elapsedMs / 1000f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    sampleFile?.let { sample ->
                        WaveformView(levels, Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(
                                R.string.sample_ready,
                                sample.name,
                                formatMs(sampleDurationMs)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = {
                            sampleFile = null
                            levels = emptyList()
                            sampleDurationMs = 0
                        }) {
                            Text(stringResource(R.string.sample_remove))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (recorder.isRecording) {
                                stopRecording()
                            } else if (hasMicPermission) {
                                levels = emptyList()
                                elapsedMs = 0
                                recording = recorder.start()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = hasMicPermission
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = null
                        )
                        Text(if (recorder.isRecording || recording) stringResource(R.string.stop) else stringResource(R.string.record))
                    }
                    OutlinedButton(onClick = { filePicker.launch("audio/*") }) {
                        Text(stringResource(R.string.pick_file))
                    }
                }
                if (!hasMicPermission) {
                    Text(
                        text = stringResource(R.string.permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.text_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.text_hint)) },
                    minLines = 4,
                    maxLines = 8,
                    supportingText = { Text(stringResource(R.string.char_count, text.length)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { languageOpen = true }) {
                        Text(Languages.labelFor(language))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = languageOpen, onDismissRequest = { languageOpen = false }) {
                        Languages.all.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.labelEn) },
                                onClick = {
                                    language = l.code
                                    languageOpen = false
                                }
                            )
                        }
                    }
                }
                Text(stringResource(R.string.speed) + " " + String.format("%.1f", speed) + "×")
                Slider(
                    value = speed,
                    onValueChange = { speed = ((it * 20).toInt() / 20f) },
                    valueRange = 0.5f..2f
                )
                if (settings.serverEffective) {
                    Text(
                        stringResource(R.string.exaggeration) + " " + String.format("%.2f", exaggeration)
                    )
                    Slider(
                        value = exaggeration,
                        onValueChange = { exaggeration = it },
                        valueRange = 0.25f..2f
                    )
                } else {
                    Text("Pitch " + String.format("%.1f", pitch))
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = ((it * 20).toInt() / 20f) },
                        valueRange = 0.5f..2f
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = consent,
                        onCheckedChange = {
                            consent = it
                            if (it) settings.updateConsentNeverAsk(true)
                        }
                    )
                    Text(stringResource(R.string.consent), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator()
                Text(stringResource(R.string.cloning), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(
                onClick = startClone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.clone), style = MaterialTheme.typography.titleMedium)
            }
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
