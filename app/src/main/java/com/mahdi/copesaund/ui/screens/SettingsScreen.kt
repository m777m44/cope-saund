package com.mahdi.copesaund.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.copesaund.R
import com.mahdi.copesaund.data.AppSettings
import com.mahdi.copesaund.model.EngineMode
import com.mahdi.copesaund.model.ThemeMode
import com.mahdi.copesaund.network.CloneClient
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: AppSettings, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf(settings.serverUrl) }
    var testState by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.engine_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.engineMode == EngineMode.AUTO,
                        onClick = { settings.setEngineMode(EngineMode.AUTO) }
                    )
                    Text(stringResource(R.string.engine_auto), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.engineMode == EngineMode.SERVER,
                        onClick = { settings.setEngineMode(EngineMode.SERVER) }
                    )
                    Text(stringResource(R.string.engine_server), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.engineMode == EngineMode.DEVICE,
                        onClick = { settings.setEngineMode(EngineMode.DEVICE) }
                    )
                    Text(stringResource(R.string.engine_device), style = MaterialTheme.typography.bodyMedium)
                }
                if (settings.engineMode != EngineMode.DEVICE) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.server_url)) },
                        placeholder = { Text(stringResource(R.string.server_url_hint)) },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            testState = context.getString(R.string.testing)
                            scope.launch {
                                val client = CloneClient(context)
                                val result = client.health(urlText.trim().trimEnd('/'))
                                if (result.isSuccess) settings.setServerUrl(urlText)
                                testState = context.getString(
                                    if (result.isSuccess) R.string.server_ok else R.string.server_bad
                                ) + (result.exceptionOrNull()?.let { " (${it.message})" } ?: "")
                            }
                        }) {
                            Text(stringResource(R.string.test_connection))
                        }
                        OutlinedButton(onClick = { settings.setServerUrl(urlText) }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                    if (testState.isNotEmpty()) {
                        Text(testState, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { settings.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    Text(stringResource(R.string.theme_system), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { settings.setThemeMode(ThemeMode.LIGHT) }
                    )
                    Text(stringResource(R.string.theme_light), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { settings.setThemeMode(ThemeMode.DARK) }
                    )
                    Text(stringResource(R.string.theme_dark), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clear_history))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Cope Saund Mahdi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "v1.0.0 · com.mahdi.copesaund",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Engine: Chatterbox (Resemble AI, MIT) / SoproTTS (Apache-2.0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Offline mode uses the Android system TTS engine (no cloning). Real voice cloning requires the lightweight server bundled in this repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
