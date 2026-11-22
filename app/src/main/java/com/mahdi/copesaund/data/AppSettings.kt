package com.mahdi.copesaund.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mahdi.copesaund.model.EngineMode
import com.mahdi.copesaund.model.ThemeMode

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("cope_saund_settings", Context.MODE_PRIVATE)

    var serverUrl: String by mutableStateOf(prefs.getString(KEY_SERVER, "") ?: "")
        private set

    var engineMode: EngineMode by mutableStateOf(
        prefs.getString(KEY_ENGINE, EngineMode.AUTO.name)?.let { mode ->
            runCatching { EngineMode.valueOf(mode) }.getOrDefault(EngineMode.AUTO)
        } ?: EngineMode.AUTO
    )
        private set

    var themeMode: ThemeMode by mutableStateOf(
        prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)?.let { mode ->
            runCatching { ThemeMode.valueOf(mode) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM
    )
        private set

    var consentNeverAsk: Boolean by mutableStateOf(prefs.getBoolean(KEY_CONSENT, false))
        private set

    val serverEffective: Boolean
        get() = when (engineMode) {
            EngineMode.SERVER -> serverUrl.isNotBlank()
            EngineMode.DEVICE -> false
            EngineMode.AUTO -> serverUrl.isNotBlank()
        }

    fun setServerUrl(value: String) {
        serverUrl = value.trim().trimEnd('/')
        prefs.edit().putString(KEY_SERVER, serverUrl).apply()
    }

    fun setEngineMode(mode: EngineMode) {
        engineMode = mode
        prefs.edit().putString(KEY_ENGINE, mode.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setConsentNeverAsk(value: Boolean) {
        consentNeverAsk = value
        prefs.edit().putBoolean(KEY_CONSENT, value).apply()
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_ENGINE = "engine_mode"
        const val KEY_THEME = "theme_mode"
        const val KEY_CONSENT = "consent_never_ask"
    }
}
