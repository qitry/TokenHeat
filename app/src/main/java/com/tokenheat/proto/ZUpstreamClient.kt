package com.tokenheat.proto

import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * ZCode upstream over its OpenAI-dialect endpoint.
 *
 * `api.z.ai/api/paas/v4` mirrors the public Zhipu OpenAI surface, so the
 * bridge body passes through with only `stream` forced — no role or
 * tool_choice rewriting, which are WorkBuddy-specific quirks. Results reuse
 * [UpstreamClient.ChatResult] so the bridge relay stays provider-agnostic.
 */
class ZUpstreamClient {

    /**
     * Opens a chat stream. The caller owns the returned connection and must
     * disconnect it, exactly like the WorkBuddy variant.
     */
    fun chatStream(credential: Credential, bodyJson: String): UpstreamClient.ChatResult {
        val body = forceStream(bodyJson)
        val conn = open(
            "$PAAS_BASE/chat/completions",
            "POST",
            mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Authorization" to "Bearer ${credential.apiKey}",
            ),
            body,
            readTimeoutMs = 0,
        ) ?: return UpstreamClient.ChatResult.Failed(0, Wire.ErrorKind.SERVER, "transport error")

        val status = runCatching { conn.responseCode }.getOrElse {
            conn.disconnect()
            return UpstreamClient.ChatResult.Failed(0, Wire.ErrorKind.SERVER, "transport error: ${it.message}")
        }
        if (status in 200..299) {
            return UpstreamClient.ChatResult.Ok(conn, conn.inputStream)
        }
        val text = runCatching { conn.errorStream?.readBytes()?.toString(StandardCharsets.UTF_8).orEmpty() }
            .getOrDefault("").take(Wire.ERROR_BODY_LIMIT)
        conn.disconnect()
        return UpstreamClient.ChatResult.Failed(status, Wire.classify(status, text), text)
    }

    /** Model ids from the standard OpenAI list shape; empty when refused. */
    fun fetchModels(credential: Credential): List<String> {
        val text = request(
            "$PAAS_BASE/models",
            "GET",
            mapOf(
                "Authorization" to "Bearer ${credential.apiKey}",
                "Accept" to "application/json",
            ),
            null,
        )
        return runCatching {
            val array = JSONObject(text).optJSONArray("data") ?: return emptyList()
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id")?.ifEmpty { null } }
        }.getOrDefault(emptyList())
    }

    /**
     * One-line quota summary for display. Coding Plan quotas live behind the
     * plan billing endpoints; anything unparseable surfaces as null so the UI
     * shows the raw failure instead of a confident wrong number.
     */
    fun fetchQuota(credential: Credential): String? {
        val headers = mapOf("x-api-key" to credential.apiKey)
        val text = runCatching { request("$BILLING_BASE/billing/balance", "GET", headers, null) }
            .getOrNull() ?: return null
        return runCatching {
            val balances = JSONObject(text).optJSONObject("data")?.optJSONArray("balances")
                ?: return null
            val parts = (0 until balances.length()).mapNotNull { i ->
                val row = balances.optJSONObject(i) ?: return@mapNotNull null
                val name = row.optString("show_name").ifEmpty { row.optString("model") }
                val remain = row.opt("remaining_units")?.toString()
                if (name.isEmpty() || remain.isNullOrEmpty()) null else "$name：剩余 $remain"
            }
            parts.ifEmpty { null }?.joinToString("\n")
        }.getOrNull()
    }

    // ------------------------------------------------------------------ //
    // Transport
    // ------------------------------------------------------------------ //

    private fun forceStream(source: String): String {
        val obj = runCatching { JSONObject(source) }.getOrNull() ?: return source
        obj.put("stream", true)
        return obj.toString()
    }

    private fun open(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        readTimeoutMs: Int = Wire.JSON_TIMEOUT_MS,
    ): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            this.readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
    }.onFailure { Log.e(TAG, "connect failed: $url", it) }.getOrNull()

    /** Sends a request and returns the body, including non-2xx bodies. */
    private fun request(url: String, method: String, headers: Map<String, String>, body: String?): String {
        val conn = open(url, method, headers, body)
            ?: throw IllegalStateException("transport error connecting to $url")
        return try {
            val stream: InputStream? =
                if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val TAG = "TokenHeat"
        const val PAAS_BASE = "https://api.z.ai/api/paas/v4"
        private const val BILLING_BASE = "https://zcode.z.ai/api/v1/zcode-plan"
    }
}
