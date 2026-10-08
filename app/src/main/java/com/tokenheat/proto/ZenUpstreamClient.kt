package com.tokenheat.proto

import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * OpenCode Zen over its OpenAI-dialect endpoint.
 *
 * Only the `chat/completions` models fit this bridge; `responses`,
 * `messages` and vendor-specific dialects need converters and are excluded
 * by intersecting the live roster with [CHAT_IDS]. The endpoint was probed
 * 2026-10: keyless calls fail closed with a client-gated free-tier error, so
 * a user API key (dashboard → paste) is required even for the $0 models.
 * Results reuse [UpstreamClient.ChatResult] so the relay stays generic.
 */
class ZenUpstreamClient {

    /** One roster entry: the id plus whether it is currently $0. */
    data class ZenModel(val id: String, val free: Boolean)

    /**
     * Opens a chat stream. The caller owns the returned connection and must
     * disconnect it, exactly like the other providers.
     */
    fun chatStream(credential: Credential, bodyJson: String): UpstreamClient.ChatResult {
        val body = forceStream(bodyJson)
        val sessionId = generateOpenCodeId("ses")
        val requestId = generateOpenCodeId("req")
        val conn = open(
            "$CHAT_BASE/chat/completions",
            "POST",
            mapOf(
                "Content-Type" to "application/json",
                "Accept" to "text/event-stream, application/json",
                "Authorization" to "Bearer ${credential.apiKey}",
                "User-Agent" to OPENCODE_USER_AGENT,
                "x-opencode-session" to sessionId,
                "x-opencode-request" to requestId,
                "x-opencode-client" to "cli",
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

    private fun generateOpenCodeId(prefix: String): String {
        val timestamp = System.currentTimeMillis()
        val now = (timestamp shl 12) or (kotlin.random.Random.nextInt(0, 0x1000).toLong())
        val hex = String.format("%012x", now)
        val base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val randomPart = (1..14).map { base62Chars[kotlin.random.Random.nextInt(base62Chars.length)] }.joinToString("")
        return "${prefix}_${hex}${randomPart}"
    }

    /**
     * Live roster (`/v1/models` needs no auth) intersected with the curated
     * chat-protocol set. Unknown ids are dropped rather than misrouted, and
     * rotated-out ids vanish on their own.
     */
    fun fetchModels(): List<ZenModel> {
        val text = runCatching {
            request("$CHAT_BASE/models", "GET", mapOf("User-Agent" to OPENCODE_USER_AGENT), null)
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONObject(text).optJSONArray("data") ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val id = array.optJSONObject(i)?.optString("id")?.ifEmpty { null } ?: return@mapNotNull null
                if (id in CHAT_IDS) ZenModel(id, id in FREE_IDS) else null
            }
        }.getOrDefault(emptyList())
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
        const val CHAT_BASE = "https://opencode.ai/zen/v1"
        const val OPENCODE_USER_AGENT = "opencode/1.18.34"

        /**
         * Model ids the official docs place on `chat/completions`. Everything
         * else (`responses`, `messages`, vendor dialects) is excluded: serving
         * one would fail at call time, not list time.
         */
        private val CHAT_IDS = setOf(
            // $0 right now (limited-time offers; the live list decides).
            "big-pickle",
            "space-bunny-free",
            "longcat-2.5-preview-free",
            "nemotron-3.5-lightning-free",
            "mimo-v2.6-flash-free",
            "mimo-v2.5-free",
            "ling-3.0-flash-fin-free",
            "nemotron-3-ultra-free",
            "deepseek-v4-flash-free",
            "minimax-m3-free",
            "qwen3.6-plus-free",
            "north-mini-code-free",
            // Metered chat models, kept because the key pays for them anyway.
            "kimi-k3",
            "qwen3.8-max",
        )

        /** Subset of [CHAT_IDS] currently offered at $0. */
        val FREE_IDS = setOf(
            "big-pickle",
            "space-bunny-free",
            "longcat-2.5-preview-free",
            "nemotron-3.5-lightning-free",
            "mimo-v2.6-flash-free",
            "mimo-v2.5-free",
            "ling-3.0-flash-fin-free",
            "nemotron-3-ultra-free",
            "deepseek-v4-flash-free",
            "minimax-m3-free",
            "qwen3.6-plus-free",
            "north-mini-code-free",
        )
    }
}
