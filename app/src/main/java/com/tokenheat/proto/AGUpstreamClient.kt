package com.tokenheat.proto

import android.util.Log
import com.tokenheat.data.AGLogin
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Antigravity upstream: Gemini-dialect `streamGenerateContent` behind the
 * Cloud Code endpoints (shape extracted from the community OAuth plugin).
 *
 * The bridge speaks OpenAI, so this client converts both directions: the
 * request becomes a wrapped Gemini body (`{project, model, request}`) and the
 * SSE answers are re-emitted as `chat.completion.chunk` frames through a pipe
 * — including a trailing `usage` block the bridge's own usage scanner picks
 * up. Only Gemini models are served; Claude-through-Antigravity needs thought
 * signature machinery that is deliberately out of scope.
 */
class AGUpstreamClient {

    /**
     * Opens a chat stream. The caller owns the returned connection and must
     * disconnect it, exactly like the other providers. Endpoints fall over
     * daily → autopush → prod on transport/5xx errors.
     */
    fun chatStream(credential: Credential, bodyJson: String): UpstreamClient.ChatResult {
        val prepared = toGeminiRequest(credential, bodyJson)
            ?: return UpstreamClient.ChatResult.Failed(0, Wire.ErrorKind.CLIENT, "unsupported request shape")
        val (model, geminiBody) = prepared
        var lastTransport: String? = null
        for (endpoint in AGLogin.ENDPOINTS) {
            val conn = open(
                "$endpoint/v1internal:streamGenerateContent?alt=sse",
                "POST",
                AGLogin.agHeaders(credential.accessToken),
                geminiBody,
                readTimeoutMs = 0,
            ) ?: continue
            val status = runCatching { conn.responseCode }.getOrNull()
            if (status == null) {
                conn.disconnect()
                lastTransport = "transport error"
                continue
            }
            if (status in 200..299) {
                return UpstreamClient.ChatResult.Ok(conn, translateStream(conn.inputStream, conn, model))
            }
            val text = runCatching { conn.errorStream?.readBytes()?.toString(StandardCharsets.UTF_8).orEmpty() }
                .getOrDefault("").take(Wire.ERROR_BODY_LIMIT)
            conn.disconnect()
            // A request-scoped refusal looks the same on every endpoint.
            if (status in 400..499 && status != 429) {
                return UpstreamClient.ChatResult.Failed(status, Wire.classify(status, text), text)
            }
            lastTransport = "http $status"
        }
        return UpstreamClient.ChatResult.Failed(0, Wire.ErrorKind.SERVER, lastTransport ?: "transport error")
    }

    /** Standard Google refresh flow (PKCE-originated clients carry no secret). Throws when refused. */
    fun refreshToken(credential: Credential): Credential {
        val body = "grant_type=refresh_token" +
            "&client_id=${urlEncode(AGLogin.CLIENT_ID)}" +
            "&client_secret=${urlEncode(AGLogin.CLIENT_SECRET)}" +
            "&refresh_token=${urlEncode(credential.refreshToken)}"
        val text = postForm("https://oauth2.googleapis.com/token", body)
        val payload = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalStateException("refresh returned non-JSON")
        if (payload.has("error")) {
            throw IllegalStateException("refresh refused: ${payload.optString("error_description", payload.optString("error"))}")
        }
        val token = payload.optString("access_token")
        if (token.isEmpty()) throw IllegalStateException("refresh returned no access token")
        return credential.copy(
            accessToken = token,
            expiresAt = System.currentTimeMillis() / 1000 + payload.optLong("expires_in", 3600L),
        )
    }

    /**
     * Roster from the account's own endpoint, intersected with the curated
     * Gemini set; falls back to the curated set when the shape is unknown so
     * a parse miss never empties the list.
     */
    fun fetchModels(credential: Credential): List<String> {
        val live = runCatching {
            val text = postJson(
                "https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
                AGLogin.agHeaders(credential.accessToken),
                JSONObject().put("project", credential.projectId).toString(),
            )
            val root = JSONObject(text)
            val array = root.optJSONArray("models")
                ?: root.optJSONArray("list")
                ?: root.optJSONObject("data")?.optJSONArray("models")
                ?: return emptyList<String>()
            (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                row.optString("id").ifEmpty { row.optString("name") }.ifEmpty { null }
            }
        }.getOrDefault(emptyList())
        val known = live.filter { it in GEMINI_IDS }
        return if (known.isNotEmpty()) known else GEMINI_IDS.toList()
    }

    /**
     * Quota buckets as display text; null when unrecognized, so the UI shows
     * the failure instead of a made-up number.
     */
    fun fetchQuota(credential: Credential): String? {
        val text = runCatching {
            postJson(
                "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota",
                AGLogin.agHeaders(credential.accessToken),
                JSONObject().put("project", credential.projectId).toString(),
            )
        }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(text)
            val buckets = root.optJSONArray("buckets")
                ?: root.optJSONObject("data")?.optJSONArray("buckets")
                ?: return null
            val parts = (0 until buckets.length()).mapNotNull { i ->
                val row = buckets.optJSONObject(i) ?: return@mapNotNull null
                val name = row.optString("model").ifEmpty { row.optString("name") }
                if (name.isEmpty()) return@mapNotNull null
                val remain = row.opt("remaining").toString().takeIf { it != "null" }
                    ?: row.opt("remainingUnits").toString().takeIf { it != "null" }
                if (remain.isNullOrEmpty()) name else "$name：剩余 $remain"
            }
            parts.ifEmpty { null }?.joinToString("\n")
        }.getOrNull()
    }

    // ------------------------------------------------------------------ //
    // OpenAI → Gemini request conversion
    // ------------------------------------------------------------------ //

    private fun toGeminiRequest(credential: Credential, source: String): Pair<String, String>? {
        val obj = runCatching { JSONObject(source) }.getOrNull() ?: return null
        val model = obj.optString("model").ifEmpty { return null }
        val contents = JSONArray()
        val system = StringBuilder()
        val declarations = JSONArray()
        val suppressTools = when (val choice = obj.opt("tool_choice")) {
            is String -> choice.trim().equals("none", ignoreCase = true)
            is JSONObject -> choice.optString("type").trim().equals("none", ignoreCase = true)
            else -> false
        }
        if (!suppressTools) {
            val tools = obj.optJSONArray("tools")
            if (tools != null) {
                for (i in 0 until tools.length()) {
                    val toolObj = tools.optJSONObject(i) ?: continue
                    val fn = toolObj.optJSONObject("function") ?: continue
                    val name = fn.optString("name")
                    if (name.isEmpty()) continue
                    declarations.put(
                        JSONObject()
                            .put("name", name)
                            .put("description", fn.optString("description"))
                            .put("parameters", fn.optJSONObject("parameters") ?: JSONObject()),
                    )
                }
            }
        }
        val messages = obj.optJSONArray("messages")
        if (messages != null) {
            for (i in 0 until messages.length()) {
                val message = messages.optJSONObject(i) ?: continue
                when (message.optString("role")) {
                    "system", "developer" -> {
                        val text = contentText(message.opt("content"))
                        if (text.isNotEmpty()) {
                            if (system.isNotEmpty()) system.append("\n")
                            system.append(text)
                        }
                    }
                    "assistant" -> {
                        val parts = JSONArray()
                        val text = contentText(message.opt("content"))
                        if (text.isNotEmpty()) parts.put(JSONObject().put("text", text))
                        val calls = message.optJSONArray("tool_calls")
                        if (calls != null) {
                            for (j in 0 until calls.length()) {
                                val call = calls.optJSONObject(j) ?: continue
                                val callFn = call.optJSONObject("function") ?: continue
                                val callName = callFn.optString("name")
                                if (callName.isEmpty()) continue
                                val args = runCatching { JSONObject(callFn.optString("args", callFn.optString("arguments"))) }
                                    .getOrElse { JSONObject().put("raw", callFn.optString("arguments")) }
                                parts.put(JSONObject().put("functionCall", JSONObject().put("name", callName).put("args", args)))
                            }
                        }
                        if (parts.length() > 0) {
                            contents.put(JSONObject().put("role", "model").put("parts", parts))
                        }
                    }
                    "tool" -> {
                        val name = message.optString("name").ifEmpty {
                            message.optString("tool_call_id").ifEmpty { "tool" }
                        }
                        contents.put(
                            JSONObject().put("role", "user").put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "functionResponse",
                                        JSONObject().put("name", name).put(
                                            "response",
                                            JSONObject().put("result", contentText(message.opt("content"))),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }
                    else -> {
                        val text = contentText(message.opt("content"))
                        if (text.isNotEmpty()) {
                            contents.put(
                                JSONObject().put("role", "user").put(
                                    "parts", JSONArray().put(JSONObject().put("text", text)),
                                ),
                            )
                        }
                    }
                }
            }
        }
        if (contents.length() == 0) return null
        val request = JSONObject().put("contents", contents)
        if (system.isNotEmpty()) {
            request.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString()))),
            )
        }
        if (declarations.length() > 0) {
            request.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
        }
        val config = JSONObject()
        obj.optDouble("temperature").takeIf { !it.isNaN() }?.let { config.put("temperature", it) }
        obj.optDouble("top_p").takeIf { !it.isNaN() }?.let { config.put("topP", it) }
        obj.optInt("max_tokens", -1).takeIf { it > 0 }?.let { config.put("maxOutputTokens", it) }
            ?: obj.optInt("max_completion_tokens", -1).takeIf { it > 0 }?.let { config.put("maxOutputTokens", it) }
        if (config.length() > 0) request.put("generationConfig", config)
        val wrapped = JSONObject()
            .put("project", credential.projectId)
            .put("model", model)
            .put("request", request)
        return model to wrapped.toString()
    }

    private fun contentText(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> (0 until content.length()).mapNotNull { i ->
            val part = content.opt(i)
            if (part is JSONObject && part.optString("type") == "text") {
                part.optString("text").ifEmpty { null }
            } else if (part is String) {
                part
            } else {
                null
            }
        }.joinToString("")
        is JSONObject -> content.optString("text")
        else -> ""
    }

    // ------------------------------------------------------------------ //
    // Gemini SSE → OpenAI chunk translation
    // ------------------------------------------------------------------ //

    /**
     * Re-emits Gemini SSE frames as OpenAI chunks on a pipe, closing with a
     * `usage` block the bridge scanner understands. A slow reader only stalls
     * this daemon thread; an early client disconnect may strand it, which is
     * accepted (same class as any relay abort).
     */
    private fun translateStream(upstream: InputStream, conn: HttpURLConnection, model: String): InputStream {
        val pipeIn = PipedInputStream(65536)
        val pipeOut = PipedOutputStream(pipeIn)
        thread(name = "ag-translate", isDaemon = true) {
            val writer = pipeOut.bufferedWriter(StandardCharsets.UTF_8)
            val id = "chatcmpl-ag-" + System.currentTimeMillis().toString(36)
            val created = System.currentTimeMillis() / 1000
            var promptTokens = 0
            var completionTokens = 0
            val toolCalls = mutableListOf<Triple<String, String, String>>()
            fun emit(delta: JSONObject, finish: String?) {
                val chunk = JSONObject()
                    .put("id", id)
                    .put("object", "chat.completion.chunk")
                    .put("created", created)
                    .put("model", model)
                    .put(
                        "choices",
                        JSONArray().put(
                            JSONObject().put("index", 0).put("delta", delta).put("finish_reason", finish),
                        ),
                    )
                writer.write("data: ")
                writer.write(chunk.toString())
                writer.write("\n\n")
                writer.flush()
            }
            try {
                upstream.bufferedReader(StandardCharsets.UTF_8).forEachLine { line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) return@forEachLine
                    val payload = trimmed.removePrefix("data:").trim()
                    if (payload == "[DONE]" || payload.isEmpty()) return@forEachLine
                    val event = runCatching { JSONObject(payload) }.getOrNull() ?: return@forEachLine
                    val partsArr = event.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                    if (partsArr != null) {
                        for (i in 0 until partsArr.length()) {
                            val part = partsArr.optJSONObject(i) ?: continue
                            // Thought parts carry no text and are skipped.
                            val text = part.optString("text")
                            if (text.isNotEmpty()) {
                                emit(JSONObject().put("content", text), null)
                            }
                            part.optJSONObject("functionCall")?.let { call ->
                                toolCalls += Triple(
                                    "call_${toolCalls.size}",
                                    call.optString("name"),
                                    call.optJSONObject("args")?.toString().orEmpty(),
                                )
                            }
                        }
                    }
                    event.optJSONObject("usageMetadata")?.let { usage ->
                        promptTokens = usage.optInt("promptTokenCount", promptTokens)
                        completionTokens = usage.optInt("candidatesTokenCount", completionTokens)
                    }
                }
            } catch (e: Exception) {
                runCatching { emit(JSONObject().put("content", "（上游中断：${e.message?.take(80)}）"), null) }
            }
            try {
                if (toolCalls.isNotEmpty()) {
                    val calls = JSONArray()
                    toolCalls.forEachIndexed { index, (callId, name, args) ->
                        calls.put(
                            JSONObject().put("index", index).put("id", callId).put("type", "function").put(
                                "function", JSONObject().put("name", name).put("arguments", args),
                            ),
                        )
                    }
                    emit(JSONObject().put("tool_calls", calls), "tool_calls")
                } else {
                    emit(JSONObject(), "stop")
                }
                val tail = JSONObject()
                    .put("id", id)
                    .put("object", "chat.completion.chunk")
                    .put("created", created)
                    .put("model", model)
                    .put("choices", JSONArray())
                    .put(
                        "usage",
                        JSONObject().put("prompt_tokens", promptTokens).put("completion_tokens", completionTokens),
                    )
                writer.write("data: ")
                writer.write(tail.toString())
                writer.write("\n\ndata: [DONE]\n\n")
                writer.flush()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "translate tail failed", e)
            } finally {
                runCatching { writer.close() }
                runCatching { upstream.close() }
                runCatching { conn.disconnect() }
            }
        }
        return pipeIn
    }

    // ------------------------------------------------------------------ //
    // Transport
    // ------------------------------------------------------------------ //

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

    private fun postForm(url: String, body: String): String {
        val conn = open(
            url, "POST",
            mapOf("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8"),
            body,
        ) ?: throw IllegalStateException("transport error connecting to $url")
        return try {
            val stream: InputStream? =
                if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, headers: Map<String, String>, body: String): String {
        val conn = open(url, "POST", headers, body)
            ?: throw IllegalStateException("transport error connecting to $url")
        return try {
            val stream: InputStream? =
                if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun urlEncode(value: String): String = try {
        java.net.URLEncoder.encode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    companion object {
        const val TAG = "TokenHeat"

        /**
         * Gemini models reachable through Antigravity (aliases resolved from
         * the community plugin). Claude-through-Antigravity is excluded: it
         * needs thought-signature machinery this bridge does not implement.
         */
        val GEMINI_IDS = listOf(
            "gemini-3-pro",
            "gemini-3-pro-high",
            "gemini-3-pro-low",
            "gemini-3-pro-preview",
            "gemini-3.1-pro",
            "gemini-3.1-pro-high",
            "gemini-3.1-pro-low",
            "gemini-3-flash",
            "gemini-3-flash-high",
            "gemini-3-flash-low",
            "gemini-3-flash-medium",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-3",
            "gemini-flash",
            "gemini-pro",
        )
    }
}
