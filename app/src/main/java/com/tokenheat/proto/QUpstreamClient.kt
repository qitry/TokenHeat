package com.tokenheat.proto

import android.util.Log
import com.tokenheat.data.QLogin
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Qoder upstream: OpenAI-dialect chat behind the account system's own
 * gateway (hosts extracted from the `@qoder-ai/qodercli` bundle).
 *
 * Inference hosts are discovered per account via
 * `/api/v5/service/region/endpoints` (cached 24h, like the CLI) with
 * hardcoded fallbacks. Results reuse [UpstreamClient.ChatResult].
 */
class QUpstreamClient {

    /**
     * Opens a chat stream. The caller owns the returned connection and must
     * disconnect it, exactly like the other providers.
     */
    fun chatStream(credential: Credential, bodyJson: String): UpstreamClient.ChatResult {
        val body = forceStream(bodyJson)
        val conn = open(
            "${inferenceBase(credential)}/model/v1/chat/completions",
            "POST",
            mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Authorization" to "Bearer ${credential.accessToken}",
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

    /** Exchanges a refresh token for a fresh device token. Throws when refused. */
    fun refreshToken(credential: Credential): Credential {
        val text = request(
            "${credential.domain}/api/v1/deviceToken/refresh",
            "POST",
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            JSONObject().put("refresh_token", credential.refreshToken).toString(),
        )
        val data = runCatching { JSONObject(text).optJSONObject("data") ?: JSONObject(text) }
            .getOrNull() ?: throw IllegalStateException("refresh returned non-JSON")
        if (data.optString("errorCode").isNotEmpty()) {
            throw IllegalStateException("refresh refused: ${data.optString("errorMessage", data.optString("errorCode"))}")
        }
        val token = data.optString("device_token").ifEmpty { data.optString("token") }
        if (token.isEmpty()) throw IllegalStateException("refresh returned no device token")
        return credential.copy(
            accessToken = token,
            refreshToken = data.optString("refresh_token").ifEmpty { credential.refreshToken },
            expiresAt = QLogin.normalizeExpiry(
                data.optLong("expires_at", 0L).takeIf { it > 0 }
                    ?: data.optLong("expire_time", 0L).takeIf { it > 0 }
                    ?: data.optLong("expires_in", 0L).takeIf { it > 0 }
                    ?: 0L,
                duration = !data.has("expires_at") && !data.has("expire_time"),
            ),
        )
    }

    /** Model ids from the catalogue; empty when the shape is unrecognized. */
    fun fetchModels(credential: Credential): List<String> {
        val text = runCatching {
            request(
                "${credential.domain}/api/v2/model/list",
                "GET",
                mapOf(
                    "Authorization" to "Bearer ${credential.accessToken}",
                    "Accept" to "application/json",
                ),
                null,
            )
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val root = JSONObject(text)
            val array = root.optJSONArray("data")
                ?: root.optJSONArray("models")
                ?: root.optJSONArray("list")
                ?: root.optJSONArray("items")
                ?: root.optJSONObject("data")?.let {
                    it.optJSONArray("models") ?: it.optJSONArray("list") ?: it.optJSONArray("items")
                } ?: return emptyList()
            (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                row.optString("id").ifEmpty {
                    row.optString("model").ifEmpty { row.optString("name") }
                }.ifEmpty { null }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * One-line quota summary for display; null when nothing recognizable is
     * returned, so the UI shows the failure instead of a made-up number.
     */
    fun fetchQuota(credential: Credential): String? {
        val text = runCatching {
            request(
                "${credential.domain}/api/v2/quota/usage",
                "GET",
                mapOf(
                    "Authorization" to "Bearer ${credential.accessToken}",
                    "Accept" to "application/json",
                ),
                null,
            )
        }.getOrNull() ?: return null
        return runCatching {
            val data = JSONObject(text).optJSONObject("data") ?: JSONObject(text)
            val keys = listOf("remaining", "remain", "balance", "quota", "total", "used", "plan", "planName")
            val parts = keys.mapNotNull { k ->
                val v = data.opt(k)?.toString()?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
                "$k: $v"
            }
            parts.ifEmpty { null }?.take(6)?.joinToString("\n")
        }.getOrNull()
    }

    // ------------------------------------------------------------------ //
    // Endpoint discovery
    // ------------------------------------------------------------------ //

    /**
     * Inference host for one account, discovered once a day. Falls back to
     * the hardcoded prod hosts (global verified 401-gated; CN to verify).
     */
    private fun inferenceBase(credential: Credential): String {
        val domain = credential.domain.ifEmpty { return fallbackBase(credential.provider) }
        synchronized(discoveryLock) {
            discoveryCache[domain]?.let { (until, base) ->
                if (System.currentTimeMillis() < until) return base
            }
        }
        val found = runCatching {
            val text = request(
                "$domain/api/v5/service/region/endpoints",
                "GET",
                mapOf(
                    "Authorization" to "Bearer ${credential.accessToken}",
                    "Accept" to "application/json",
                ),
                null,
            )
            val data = JSONObject(text).optJSONObject("data") ?: JSONObject(text)
            val inference = data.optJSONArray("inference") ?: return@runCatching null
            (0 until inference.length())
                .mapNotNull { inference.optString(it).ifEmpty { null } }
                .firstOrNull { it.startsWith("http") }
                ?.trimEnd('/')
        }.getOrNull()
        val base = found ?: fallbackBase(credential.provider)
        synchronized(discoveryLock) {
            discoveryCache[domain] = System.currentTimeMillis() + DISCOVERY_TTL_MS to base
        }
        return base
    }

    private fun fallbackBase(provider: Provider): String = when (provider) {
        Provider.QODER_CN -> "https://api2-v2.qoder.com.cn"
        else -> "https://api2-v2.qoder.sh"
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
        private const val DISCOVERY_TTL_MS = 24 * 60 * 60 * 1000L
        private val discoveryLock = Any()
        private val discoveryCache = HashMap<String, Pair<Long, String>>()
    }
}
