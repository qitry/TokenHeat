package app.anylyralabs.lyraproxy.qoder

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Qoder 聊天流的适配层。
 *
 * Qoder 的 SSE 是双层的：外层是 `{statusCodeValue, body}`，内层才是
 * 标准的 `choices[].delta`。这里把内层重新封装成 OpenAI 兼容的 SSE，
 * 网关就能复用现有的转发逻辑，不必为每个上游写一套解析。
 */
object QoderChat {

    sealed class Result {
        data class Ok(val stream: InputStream) : Result()
        /** 非流式请求：一次性返回的完整响应。 */
        data class Json(val body: String) : Result()
        data class Failed(val status: Int, val message: String) : Result()
    }

    /**
     * 发起一次聊天。返回的流是 OpenAI 兼容的 SSE 字节流。
     *
     * @param models 已缓存的模型目录，用于把客户端模型 ID 映射回上游 key
     */
    fun open(
        account: QoderAccount,
        openAiBody: String,
        models: List<QoderModel>,
        forwardReasoning: Boolean = false,
    ): Result {
        val request = runCatching { JSONObject(openAiBody) }.getOrElse {
            return Result.Failed(400, "invalid json")
        }
        val modelId = request.optString("model")
        val model = models.firstOrNull { it.id == modelId }
            ?: models.firstOrNull()
            ?: return Result.Failed(400, "no qoder model available")

        val (messages, systemText, lastUserText) = convertMessages(request.optJSONArray("messages"))
        val maxTokens = request.optInt("max_tokens", 0).takeIf { it > 0 } ?: 8192
        // 客户端要求思考时才打开上游的 thinking，否则保持关闭。
        val effort = normalizeEffort(
            request.optString("reasoning_effort").ifBlank {
                if (request.optBoolean("thinking", false)) "medium" else ""
            },
        )
        val tools = request.optJSONArray("tools")?.let { normalizeTools(it) } ?: JSONArray()

        val recordId = UUID.randomUUID().toString().replace("-", "").take(16)
        val bodyJson = JSONObject().apply {
            put("request_id", UUID.randomUUID().toString())
            put("request_set_id", recordId)
            put("chat_record_id", recordId)
            put("session_id", "${account.id.take(8)}-$recordId")
            put("stream", true)
            put("chat_task", "FREE_INPUT")
            put("chat_context", chatContext(lastUserText, model))
            put("is_reply", true)
            put("is_retry", false)
            put("source", 1)
            put("version", "3")
            put("agent_id", "agent_common")
            put("task_id", "common")
            put("session_type", "qodercli")
            put("aliyun_user_type", "")
            put("model_config", modelConfig(model))
            put("system", systemText)
            put("messages", messages)
            put("tools", tools)
            put("parameters", JSONObject().apply {
                put("max_tokens", maxTokens)
                put("enable_thinking", effort.isNotBlank())
                if (effort.isNotBlank()) put("reasoning_effort", effort)
            })
        }.toString()

        // 上游要求正文按自定义 Base64 编码，签名与哈希都基于编码后的字节。
        val encodedBody = QoderBodyEncoder.encode(bodyJson.toByteArray(Charsets.UTF_8))

        val path = "/api/v2/service/pro/sse/agent_chat_generation"
        val signed = CosySigner.sign(
            method = "POST",
            path = path,
            body = encodedBody,
            token = account.accessToken,
            uid = account.id,
            name = account.name,
            email = account.email,
            machineId = account.machineId,
        )
        val url = "${QoderConfig.GATEWAY_URL}/algo$path" +
            "?FetchKeys=llm_model_result&AgentId=agent_common&Encode=1"

        var connection: HttpURLConnection? = null
        val status: Int
        val upstream: InputStream
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 0
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("X-Model-Key", model.key)
                setRequestProperty("X-Model-Source", "system")
                signed.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                outputStream.use { it.write(encodedBody) }
            }
            status = connection.responseCode
            upstream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            if (status !in 200..299) {
                val text = upstream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
                connection.disconnect()
                return Result.Failed(status, text.take(300))
            }
        } catch (error: Exception) {
            runCatching { connection?.disconnect() }
            return Result.Failed(0, error.message ?: "transport error")
        }

        val pipeIn = PipedInputStream(64 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        val conn = connection
        thread(isDaemon = true, name = "qoder-chat-relay") {
            try {
                OutputStreamWriter(pipeOut, StandardCharsets.UTF_8).use { writer ->
                    relayFrames(
                        upstream = upstream,
                        writer = writer,
                        forwardReasoning = forwardReasoning,
                    )
                }
            } catch (_: Exception) {
                // 客户端断开或上游中断：关掉流即可，错误已经在帧里表达。
            } finally {
                runCatching { upstream.close() }
                runCatching { conn.disconnect() }
                runCatching { pipeOut.close() }
            }
        }
        return Result.Ok(pipeIn)
    }

    /**
     * 非流式请求。
     *
     * 复用流式链路，把增量拼成完整回答，再包装成标准的 `chat.completion`。
     * 很多客户端默认发送 `stream: false`，若仍回 SSE 会被判为格式错误。
     */
    fun complete(
        account: QoderAccount,
        openAiBody: String,
        models: List<QoderModel>,
        forwardReasoning: Boolean = false,
    ): Result {
        val opened = open(account, openAiBody, models, forwardReasoning)
        if (opened !is Result.Ok) return opened

        val requestedModel = runCatching { JSONObject(openAiBody).optString("model") }
            .getOrDefault("")
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
        var created = System.currentTimeMillis() / 1000
        var promptTokens = 0
        var completionTokens = 0
        var error: String? = null
        var finishReason = ""
        val toolCalls = LinkedHashMap<Int, JSONObject>()

        opened.stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                obj.optJSONObject("error")?.let {
                    error = it.optString("message")
                    return@let
                }
                if (error != null) break
                id = obj.optString("id", id)
                created = obj.optLong("created", created)
                obj.optJSONObject("usage")?.let { usage ->
                    promptTokens = usage.optInt("prompt_tokens", promptTokens)
                    completionTokens = usage.optInt("completion_tokens", completionTokens)
                }
                val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: continue
                if (!choice.isNull("finish_reason")) {
                    val reason = choice.optString("finish_reason")
                    if (reason.isNotBlank()) finishReason = reason
                }
                val delta = choice.optJSONObject("delta") ?: continue
                content.append(delta.optString("content"))
                reasoning.append(delta.optString("reasoning_content"))
                delta.optJSONArray("tool_calls")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val slot = part.optInt("index", i)
                        val acc = toolCalls.getOrPut(slot) {
                            JSONObject().apply {
                                put("id", "")
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", "")
                                    put("arguments", "")
                                })
                            }
                        }
                        part.optString("id").takeIf { it.isNotBlank() }?.let { acc.put("id", it) }
                        part.optString("type").takeIf { it.isNotBlank() }?.let { acc.put("type", it) }
                        val fn = part.optJSONObject("function") ?: continue
                        val target = acc.optJSONObject("function") ?: continue
                        fn.optString("name").takeIf { it.isNotBlank() }?.let { target.put("name", it) }
                        fn.optString("arguments").takeIf { it.isNotEmpty() }?.let {
                            target.put("arguments", target.optString("arguments") + it)
                        }
                    }
                }
            }
        }

        val failure = error
        if (failure != null) return Result.Failed(502, failure)

        val calls = JSONArray().apply { toolCalls.values.forEach { put(it) } }
        val message = JSONObject().apply {
            put("role", "assistant")
            // 纯工具调用时 content 允许为空串，OpenAI 客户端按 tool_calls 处理。
            put("content", content.toString().ifEmpty { if (calls.length() > 0) "" else "" })
            // reasoning_content 不是 OpenAI 标准字段，只有显式开启才回传。
            if (forwardReasoning && reasoning.isNotEmpty()) {
                put("reasoning_content", reasoning.toString())
            }
            if (calls.length() > 0) put("tool_calls", calls)
        }
        val json = JSONObject().apply {
            put("id", id)
            put("object", "chat.completion")
            put("created", created)
            put("model", requestedModel)
            put("system_fingerprint", JSONObject.NULL)
            put(
                "choices",
                JSONArray().put(
                    JSONObject().apply {
                        put("index", 0)
                        put("message", message)
                        put("logprobs", JSONObject.NULL)
                        put(
                            "finish_reason",
                            when {
                                calls.length() > 0 -> "tool_calls"
                                finishReason.isNotBlank() -> finishReason
                                else -> "stop"
                            },
                        )
                    },
                ),
            )
            put(
                "usage",
                JSONObject().apply {
                    put("prompt_tokens", promptTokens)
                    put("completion_tokens", completionTokens)
                    put("total_tokens", promptTokens + completionTokens)
                },
            )
        }
        return Result.Json(json.toString())
    }

    // --------------------------------------------------------------------- //

    private data class Converted(
        val messages: JSONArray,
        val system: String,
        val lastUser: String,
    )

    /** OpenAI 消息 → Qoder 消息；system 单独抽出来，因为上游只认顶层 system 字段。 */
    private fun convertMessages(messages: JSONArray?): Converted {
        val result = JSONArray()
        val systemParts = ArrayList<String>()
        var lastUser = ""
        if (messages != null) {
            for (index in 0 until messages.length()) {
                val message = messages.optJSONObject(index) ?: continue
                val role = message.optString("role")
                val content = contentToText(message.opt("content"))
                when (role) {
                    "system", "developer" -> systemParts.add(content)
                    "assistant" -> result.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", content)
                            // 工具调用的上下文必须原样带回上游，否则模型只看到
                            // 一条空 assistant 消息和一条对不上号的 tool 结果。
                            message.optJSONArray("tool_calls")?.let { calls ->
                                if (calls.length() > 0) put("tool_calls", normalizeToolCalls(calls))
                            }
                        },
                    )
                    "tool" -> result.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", message.optString("tool_call_id"))
                            if (message.has("name")) put("name", message.optString("name"))
                            put("content", content)
                        },
                    )
                    else -> {
                        lastUser = content
                        result.put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", content)
                            },
                        )
                    }
                }
            }
        }
        return Converted(result, systemParts.joinToString("\n"), lastUser)
    }

    private fun contentToText(content: Any?): String = when (content) {
        null -> ""
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }
        else -> content.toString()
    }

    /** 客户端回传的 tool_calls 统一成 OpenAI 结构，arguments 必须是字符串。 */
    private fun normalizeToolCalls(calls: JSONArray): JSONArray {
        val out = JSONArray()
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index) ?: continue
            val function = call.optJSONObject("function") ?: continue
            val arguments = function.opt("arguments")
            out.put(
                JSONObject().apply {
                    put("id", call.optString("id"))
                    put("type", call.optString("type").ifBlank { "function" })
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", function.optString("name"))
                            put(
                                "arguments",
                                when (arguments) {
                                    is String -> arguments
                                    null, JSONObject.NULL -> "{}"
                                    else -> arguments.toString()
                                },
                            )
                        },
                    )
                },
            )
        }
        return out
    }

    private fun normalizeTools(tools: JSONArray): JSONArray {
        val result = JSONArray()
        for (index in 0 until tools.length()) {
            val tool = tools.optJSONObject(index) ?: continue
            val function = tool.optJSONObject("function") ?: continue
            result.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", function.optString("name"))
                            put("description", function.optString("description"))
                            put("parameters", function.optJSONObject("parameters") ?: JSONObject())
                            if (function.has("strict")) put("strict", function.optBoolean("strict"))
                        },
                    )
                },
            )
        }
        return result
    }

    private fun chatContext(lastUser: String, model: QoderModel): JSONObject = JSONObject().apply {
        put("text", lastUser)
        put("features", JSONArray())
        put(
            "extra",
            JSONObject().apply {
                put("context", JSONArray())
                put(
                    "modelConfig",
                    JSONObject().apply {
                        put("key", model.key)
                        put("is_reasoning", model.supportsReasoning)
                    },
                )
                put("originalContent", lastUser)
            },
        )
        put("chatPrompt", "")
        put("imageUrls", JSONObject.NULL)
    }

    /** 优先回传上游目录的原始条目；缺失时用最小可用结构兜底。 */
    private fun modelConfig(model: QoderModel): JSONObject {
        if (model.raw.isNotBlank()) {
            runCatching { JSONObject(model.raw) }.getOrNull()?.let { return it }
        }
        return JSONObject().apply {
            put("key", model.key)
            put("display_name", model.displayName)
            put("is_reasoning", model.supportsReasoning)
            put("is_vl", model.supportsImages)
            put("enable", true)
            put("source", "system")
            put("format", "openai")
        }
    }

    // --------------------------------------------------------------------- //
    // SSE 转换
    // --------------------------------------------------------------------- //

    /**
     * 转发上游帧。
     *
     * 与 WorkBuddy 的做法保持一致：上游 `body` 本身就是一个完整的
     * OpenAI chunk（含 choices / created / id / system_fingerprint），
     * 解开外层信封后原样透传，不重新拼装字段。
     *
     * 这样思考内容、工具调用、结束帧、usage 都跟着上游走，
     * 不会因为本地字段裁剪而让客户端解析失败。
     */
    private fun relayFrames(
        upstream: InputStream,
        writer: OutputStreamWriter,
        forwardReasoning: Boolean,
    ) {
        val reader = upstream.bufferedReader(StandardCharsets.UTF_8)
        var sawChunk = false
        var lastError: String? = null
        var currentEvent = ""
        val rawSamples = ArrayList<String>(4)
        // 上游有时不发 finish 帧（例如 event: finish 带非 200 被跳过）。
        // 客户端收不到 finish_reason 会把这一轮当成被截断，于是重试或直接断开。
        // 这里补一帧结束标记，保证每轮都有明确的收尾。
        var hasFinish = false
        var sawToolCall = false
        var lastId = "chatcmpl-lyra"
        var lastCreated = System.currentTimeMillis() / 1000
        var lastModel = "lyra"

        while (true) {
            val line = reader.readLine() ?: break

            if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim()
                continue
            }
            if (!line.startsWith("data:")) continue

            val payload = line.substring(5).trim()
            if (payload.isEmpty()) continue
            // [DONE] 只是上游的载荷标记，官方 CLI 把它当空载荷跳过；
            // 直接 break 会把「正文之后、工具调用/下一段思考之前」的帧全部丢掉。
            if (payload == "[DONE]") continue
            if (isControlPayload(payload)) continue
            if (rawSamples.size < 4) rawSamples.add(payload.take(200))

            val envelope = runCatching { JSONObject(payload) }.getOrNull()
            if (envelope == null) {
                lastError = "unparsable frame: ${payload.take(200)}"
                continue
            }

            val statusCode = envelope.optInt("statusCodeValue", envelope.optInt("statusCode", 200))
            // finish 事件允许带非 200 状态，它表示「这一步结束」，不是失败。
            if (statusCode != 200 && currentEvent != "finish") {
                lastError = "upstream $statusCode: ${envelope.optString("body").take(300)}"
                currentEvent = ""
                continue
            }
            currentEvent = ""

            var inner = envelope.optString("body")
            if (inner == "[DONE]") continue
            if (inner.isEmpty() || isControlPayload(inner)) continue

            // 解析一份原始帧，记录 id / model / finish_reason / 是否出现工具调用。
            runCatching { JSONObject(inner) }.getOrNull()?.let { frame ->
                frame.optString("id").takeIf { it.isNotBlank() }?.let { lastId = it }
                frame.optLong("created", 0L).takeIf { it > 0 }?.let { lastCreated = it }
                frame.optString("model").takeIf { it.isNotBlank() }?.let { lastModel = it }
                val choice = frame.optJSONArray("choices")?.optJSONObject(0)
                if (choice != null) {
                    if (!choice.isNull("finish_reason")) hasFinish = true
                    val delta = choice.optJSONObject("delta")
                    if ((delta?.optJSONArray("tool_calls")?.length() ?: 0) > 0) sawToolCall = true
                }
            }

            // 流式 tool_calls 必须带 index，客户端按它把增量拼回同一条调用。
            // 上游偶尔省略 index，缺了就会把两段参数拼错、甚至截断。
            val patched = patchToolCallIndexes(inner)
            if (patched != null) inner = patched

            if (!forwardReasoning) {
                inner = stripReasoning(inner)
            }

            writer.write("data: $inner\n\n")
            writer.flush()
            sawChunk = true
        }

        if (!sawChunk) {
            emitError(
                writer,
                lastError ?: "no content in upstream frames; samples=${rawSamples.joinToString(" | ")}",
            )
            return
        }

        // 上游没给结束帧时补一帧，避免客户端把正常结束当成断流。
        if (!hasFinish) {
            val tail = JSONObject().apply {
                put("id", lastId)
                put("object", "chat.completion.chunk")
                put("created", lastCreated)
                put("model", lastModel)
                put("system_fingerprint", JSONObject.NULL)
                put(
                    "choices",
                    JSONArray().put(
                        JSONObject().apply {
                            put("index", 0)
                            put("delta", JSONObject())
                            put("logprobs", JSONObject.NULL)
                            put(
                                "finish_reason",
                                if (sawToolCall) "tool_calls" else "stop",
                            )
                        },
                    ),
                )
            }
            writer.write("data: $tail\n\n")
        }

        writer.write("data: [DONE]\n\n")
        writer.flush()
    }

    /** 给缺失 index 的流式 tool_calls 补上下标；没有工具调用时返回 null。 */
    private fun patchToolCallIndexes(inner: String): String? {
        val json = runCatching { JSONObject(inner) }.getOrNull() ?: return null
        val delta = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?: return null
        val calls = delta.optJSONArray("tool_calls") ?: return null
        var changed = false
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            if (!call.has("index")) {
                call.put("index", i)
                changed = true
            }
        }
        return if (changed) json.toString() else null
    }

    /** 关闭思考转发时，从 chunk 的 delta 里移除推理字段。 */
    private fun stripReasoning(inner: String): String {
        val json = runCatching { JSONObject(inner) }.getOrNull() ?: return inner
        val choices = json.optJSONArray("choices") ?: return inner
        var changed = false
        for (index in 0 until choices.length()) {
            val delta = choices.optJSONObject(index)?.optJSONObject("delta") ?: continue
            listOf("reasoning_content", "reasoning", "reasoning_text").forEach { key ->
                if (delta.has(key)) {
                    delta.remove(key)
                    changed = true
                }
            }
        }
        return if (changed) json.toString() else inner
    }

    /** 把客户端可能使用的思考档位映射到上游接受的值。 */
    private fun normalizeEffort(value: String): String = when (value.trim().lowercase()) {
        "low", "minimal" -> "low"
        "high", "xhigh", "max" -> "high"
        "medium" -> "medium"
        "", "none", "off", "disabled" -> ""
        else -> "medium"
    }

    /** Qoder 的控制帧：额度提示与通知，不承载回答内容。 */
    private fun isControlPayload(value: String): Boolean =
        value == "[NOT_EXCEED_QUOTA]" ||
            value.startsWith("[EXCEED_QUOTA]") ||
            value.startsWith("[NOTIFICATIONS]")

    private fun emitError(writer: OutputStreamWriter, message: String) {
        emit(
            writer,
            JSONObject().apply {
                put("error", JSONObject().put("message", message).put("type", "qoder_upstream"))
            }.toString(),
        )
        writer.write("data: [DONE]\n\n")
        writer.flush()
    }

    private fun emit(writer: OutputStreamWriter, payload: String) {
        writer.write("data: $payload\n\n")
        writer.flush()
    }
}
