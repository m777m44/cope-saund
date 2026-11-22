package com.mahdi.copesaund.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class CloneClient(private val context: Context) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    suspend fun health(serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$serverUrl/health").get().build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                resp.body?.string().orEmpty()
            }
        }
    }

    suspend fun clone(
        serverUrl: String,
        text: String,
        language: String,
        speed: Float,
        exaggeration: Float,
        ref: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("text", text)
                .addFormDataPart("language", language)
                .addFormDataPart("speed", speed.toString())
                .addFormDataPart("exaggeration", exaggeration.toString())
                .addFormDataPart(
                    "ref", ref.name, ref.asRequestBody("audio/wav".toMediaType())
                )
                .build()
            val req = Request.Builder().url("$serverUrl/clone").post(body).build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val bytes = resp.body?.bytes() ?: error("Empty response")
                val dir = File(context.filesDir, "exports").apply { mkdirs() }
                val out = File(dir, "clone_${System.currentTimeMillis()}.wav")
                out.writeBytes(bytes)
                out
            }
        }
    }
}
