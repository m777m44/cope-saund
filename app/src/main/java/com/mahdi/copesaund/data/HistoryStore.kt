package com.mahdi.copesaund.data

import android.content.Context
import com.mahdi.copesaund.model.HistoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.json")

    fun load(): MutableList<HistoryItem> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                HistoryItem(
                    id = o.getString("id"),
                    text = o.optString("text"),
                    language = o.optString("language", "auto"),
                    engine = o.optString("engine", ""),
                    createdAt = o.getLong("createdAt"),
                    filePath = o.getString("filePath"),
                    durationMs = o.optLong("durationMs", 0L)
                ).takeIf { File(it.filePath).exists() }
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun add(items: MutableList<HistoryItem>, item: HistoryItem) {
        items.add(0, item)
        persist(items)
    }

    fun remove(items: MutableList<HistoryItem>, id: String) {
        val removed = items.firstOrNull { it.id == id }
        items.removeAll { it.id == id }
        persist(items)
        removed?.let { runCatching { File(it.filePath).delete() } }
    }

    fun clear(items: MutableList<HistoryItem>) {
        items.forEach { runCatching { File(it.filePath).delete() } }
        items.clear()
        persist(items)
    }

    private fun persist(items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("language", it.language)
                    .put("engine", it.engine)
                    .put("createdAt", it.createdAt)
                    .put("filePath", it.filePath)
                    .put("durationMs", it.durationMs)
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }
}
