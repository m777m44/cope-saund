package com.mahdi.copesaund.model

enum class EngineMode { AUTO, SERVER, DEVICE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Language(val code: String, val labelEn: String)

object Languages {
    val all = listOf(
        Language("auto", "Auto"),
        Language("ar", "Arabic"),
        Language("en", "English"),
        Language("fr", "French"),
        Language("es", "Spanish"),
        Language("de", "German"),
        Language("it", "Italian"),
        Language("pt", "Portuguese"),
        Language("ru", "Russian"),
        Language("tr", "Turkish"),
        Language("hi", "Hindi"),
        Language("zh", "Chinese")
    )

    fun labelFor(code: String): String = all.firstOrNull { it.code == code }?.labelEn ?: code
}

data class HistoryItem(
    val id: String,
    val text: String,
    val language: String,
    val engine: String,
    val createdAt: Long,
    val filePath: String,
    val durationMs: Long
)
