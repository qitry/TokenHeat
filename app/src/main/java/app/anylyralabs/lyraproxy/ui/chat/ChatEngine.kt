// Copyright 2026, LyraProxy contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui.chat

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 本机网关的流式聊天客户端。
 *
 * - 请求 `http://127.0.0.1:<port>/v1/chat/completions`，`Authorization: Bearer <secret>`（secret 为空时不发）。
 * - 逐行解析 SSE：只取 `data:` 行，读 `choices[0].delta.content` / `delta.reasoning_content` / `finish_reason`，
 *   遇到 `data: [DONE]` 结束；网关如果直接回整段 JSON 也能解析。
 * - 工具循环：模型返回 `tool_calls` 时执行 [ChatTools]，把结果作为 `role=tool` 消息回填后再发下一轮，
 *   最多执行 [MAX_TOOL_ROUNDS] 轮；之后不再带 tools，强制模型直接作答。
 * - 网络和解析都在工作线程；[onDelta] / [onSources] / [onDone] 统一切回主线程。
 *
 * 调用方（ChatScreen）负责把 [ChatMessage] 拼好传进来，工具消息不会出现在会话列表里。
 */
class ChatEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)

    @Volatile
    private var connection: HttpURLConnection? = null

    /** 停止当前生成：断开连接并让所有回调静默（界面自己把 streaming 收尾）。 */
    fun stop() {
        generation.incrementAndGet()
        val current = connection
        connection = null
        runCatching { current?.disconnect() }
    }

    /**
     * 开始一轮生成。重复调用会先停掉上一轮。
     *
     * @param onDelta 增量回调：本次新增的正文 / 思维链片段（可能其中一个为空串）。
     * @param onSources 累计到的来源（每轮工具结果后回调一次，已按 url 去重）。
     * @param onDone 结束回调，`error == null` 表示正常完成；被 [stop] 打断时不会再回调。
     */
    fun stream(
        messages: List<ChatMessage>,
        model: String,
        port: Int,
        secret: String,
        toolsEnabled: Boolean,
        exaKey: String,
        firecrawlKey: String,
        onDelta: (content: String, reasoning: String) -> Unit,
        onSources: (List<ChatSource>) -> Unit,
        onDone: (error: String?) -> Unit,
    ) {
        stop()
        val myGeneration = generation.incrementAndGet()

        val worker = Thread({
            var error: String? = null
            try {
                runConversation(
                    generation = myGeneration,
                    messages = messages,
                    model = model,
                    port = port,
                    secret = secret,
                    toolsEnabled = toolsEnabled,
                    exaKey = exaKey,
                    firecrawlKey = firecrawlKey,
                    onDelta = onDelta,
                    onSources = onSources,
                )
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                postIfActive(myGeneration) { onDone(error) }
            }
        }, "lyra-chat-stream")
        worker.isDaemon = true
        worker.start()
    }

    // ───────────────────────── 对话 / 工具循环 ─────────────────────────

    private fun runConversation(
        generation: Int,
        messages: List<ChatMessage>,
        model: String,
        port: Int,
        secret: String,
        toolsEnabled: Boolean,
        exaKey: String,
        firecrawlKey: String,
        onDelta: (String, String) -> Unit,
        onSources: (List<ChatSource>) -> Unit,
    ) {
        val working = messagesToJson(messages)
        val sources = LinkedHashMap<String, ChatSource>()
        var toolRounds = 0

        while (true) {
            if (!isActive(generation)) return
            val allowTools = toolsEnabled && toolRounds < MAX_TOOL_ROUNDS
            val outcome = requestRound(
                generation = generation,
                messages = working,
                model = model,
                port = port,
                secret = secret,
                allowTools = allowTools,
                onDelta = onDelta,
            )
            if (!isActive(generation)) return

            if (outcome.toolCalls.isEmpty() || !allowTools) return
            toolRounds++

            // 把「助手要调用工具」这一步按 OpenAI 格式回填，否则网关会认为 tool 消息没有对应的 call。
            val callsJson = JSONArray()
            outcome.toolCalls.forEach { call ->
                callsJson.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", "function")
                        .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments)),
                )
            }
            working.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", outcome.content)
                    .put("tool_calls", callsJson),
            )

            outcome.toolCalls.forEach { call ->
                if (!isActive(generation)) return
                val result = ChatTools.execute(call.name, call.arguments, exaKey, firecrawlKey)
                val toolText = when (result) {
                    is ToolResult.Ok -> {
                        result.sources.forEach { source -> sources.putIfAbsent(source.url, source) }
                        result.text
                    }

                    is ToolResult.Unavailable ->
                        "工具当前不可用：${result.reason}。请直接用已有知识回答，不要再次调用该工具。"

                    is ToolResult.Failed ->
                        "工具执行失败：${result.reason}。请直接回答或换一种方式继续。"
                }
                working.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.id)
                        .put("content", toolText.take(MAX_TOOL_RESULT_CHARS)),
                )
            }

            if (sources.isNotEmpty()) {
                val snapshot = sources.values.toList()
                postIfActive(generation) { onSources(snapshot) }
            }
        }
    }

    // ───────────────────────── 单轮请求 ─────────────────────────

    private fun requestRound(
        generation: Int,
        messages: JSONArray,
        model: String,
        port: Int,
        secret: String,
        allowTools: Boolean,
        onDelta: (String, String) -> Unit,
    ): RoundOutcome {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            if (allowTools) {
                put("tools", toolDefinitions())
                put("tool_choice", "auto")
            }
        }

        val url = URL("http://127.0.0.1:$port/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Accept-Encoding", "identity")
            if (secret.isNotBlank()) setRequestProperty("Authorization", "Bearer $secret")
        }
        connection = conn

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val raw = readLimited(conn.errorStream, MAX_ERROR_CHARS)
                // 网关或上游不支持 tools 时会直接 400 / 422：去掉工具再试一次，聊天不至于被卡死
                if (allowTools && (code == 400 || code == 422)) {
                    conn.disconnect()
                    if (connection === conn) connection = null
                    return requestRound(
                        generation = generation,
                        messages = messages,
                        model = model,
                        port = port,
                        secret = secret,
                        allowTools = false,
                        onDelta = onDelta,
                    )
                }
                throw IOException("网关返回 HTTP $code：${gatewayError(raw)}")
            }

            val contentType = conn.contentType.orEmpty()
            return if (contentType.contains("application/json", ignoreCase = true) &&
                !contentType.contains("event-stream", ignoreCase = true)
            ) {
                val raw = readLimited(conn.inputStream, MAX_RESPONSE_CHARS)
                parseWholeJson(generation, raw, onDelta)
            } else {
                readSse(generation, conn.inputStream, onDelta)
            }
        } finally {
            if (connection === conn) connection = null
            conn.disconnect()
        }
    }

    private fun readSse(
        generation: Int,
        stream: InputStream,
        onDelta: (String, String) -> Unit,
    ): RoundOutcome {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val calls = LinkedHashMap<Int, ToolCallAcc>()
        var finishReason: String? = null

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line = reader.readLine() ?: return RoundOutcome("", "", emptyList(), null)

            // 防呆：有的网关忽略了 stream=true，直接回整段 JSON
            if (line.trimStart().startsWith("{")) {
                val whole = buildString {
                    append(line)
                    while (true) {
                        val next = reader.readLine() ?: break
                        append('\n').append(next)
                    }
                }
                return parseWholeJson(generation, whole, onDelta)
            }

            while (isActive(generation)) {
                val trimmed = line.trim()
                line = reader.readLine() ?: break
                if (trimmed.isEmpty() || trimmed.startsWith(":") || trimmed.startsWith("event:")) continue
                if (!trimmed.startsWith("data:")) continue

                val payload = trimmed.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break

                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                val errorObj = json.optJSONObject("error")
                if (errorObj != null) {
                    throw IOException(errorObj.optString("message").ifBlank { "网关在流中返回了错误" })
                }

                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                choice.optString("finish_reason")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { finishReason = it }

                val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: continue
                val contentDelta = delta.text("content")
                val reasoningDelta = delta.text("reasoning_content").ifBlank { delta.text("reasoning") }

                if (contentDelta.isNotEmpty()) {
                    content.append(contentDelta)
                    postIfActive(generation) { onDelta(contentDelta, "") }
                }
                if (reasoningDelta.isNotEmpty()) {
                    reasoning.append(reasoningDelta)
                    postIfActive(generation) { onDelta("", reasoningDelta) }
                }
                collectToolCalls(delta, calls)
            }
        }

        return RoundOutcome(
            content = content.toString(),
            reasoning = reasoning.toString(),
            toolCalls = calls.toSortedMap().values.mapIndexed { index, acc ->
                ToolCall(
                    id = acc.id.ifBlank { "call_$index" },
                    name = acc.name.trim(),
                    arguments = acc.arguments.toString(),
                )
            }.filter { it.name.isNotBlank() },
            finishReason = finishReason,
        )
    }

    /** 非流式（或网关忽略 stream）时的整段 JSON。 */
    private fun parseWholeJson(
        generation: Int,
        raw: String,
        onDelta: (String, String) -> Unit,
    ): RoundOutcome {
        if (raw.isBlank()) throw IOException("网关返回了空响应")
        val root = JSONObject(raw)
        val errorObj = root.optJSONObject("error")
        if (errorObj != null) {
            throw IOException(errorObj.optString("message").ifBlank { "网关返回了错误" })
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: return RoundOutcome("", "", emptyList(), null)
        val message = choice.optJSONObject("message") ?: JSONObject()
        val content = message.text("content")
        val reasoning = message.text("reasoning_content").ifBlank { message.text("reasoning") }

        if (content.isNotEmpty()) postIfActive(generation) { onDelta(content, "") }
        if (reasoning.isNotEmpty()) postIfActive(generation) { onDelta("", reasoning) }

        val calls = ArrayList<ToolCall>()
        val array = message.optJSONArray("tool_calls") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val function = item.optJSONObject("function") ?: continue
            val name = function.optString("name").trim()
            if (name.isBlank()) continue
            calls.add(
                ToolCall(
                    id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: "call_$i",
                    name = name,
                    arguments = function.optString("arguments"),
                ),
            )
        }

        return RoundOutcome(
            content = content,
            reasoning = reasoning,
            toolCalls = calls,
            finishReason = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun collectToolCalls(delta: JSONObject, calls: MutableMap<Int, ToolCallAcc>) {
        val array = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val index = if (item.has("index")) item.optInt("index", i) else i
            val acc = calls.getOrPut(index) { ToolCallAcc() }

            val id = item.optString("id")
            if (id.isNotBlank() && id != "null") acc.id = id

            val function = item.optJSONObject("function") ?: continue
            val name = function.optString("name")
            if (name.isNotBlank() && name != "null" && name != acc.name) {
                // 有的网关每块都重复完整名字，有的把名字拆成多块
                acc.name = if (acc.name.isEmpty() || name.startsWith(acc.name)) name else acc.name + name
            }
            val arguments = function.text("arguments")
            if (arguments.isNotEmpty()) acc.arguments.append(arguments)
        }
    }

    // ───────────────────────── 请求体 ─────────────────────────

    private fun messagesToJson(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        messages.forEach { message ->
            val role = when (message.role) {
                ChatMessage.ROLE_USER -> "user"
                ChatMessage.ROLE_ASSISTANT -> "assistant"
                else -> "system"
            }
            // 流式占位（内容还是空的助手消息）不发给网关
            if (role == "assistant" && message.content.isBlank()) return@forEach
            array.put(JSONObject().put("role", role).put("content", message.content))
        }
        return array
    }

    private fun toolDefinitions(): JSONArray = JSONArray().apply {
        put(
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", ChatTools.WEB_SEARCH)
                        .put("description", "在互联网上搜索资料。需要最新信息、事实核查或你不确定的内容时使用。")
                        .put(
                            "parameters",
                            JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties",
                                    JSONObject().put(
                                        "query",
                                        JSONObject().put("type", "string").put("description", "搜索关键词"),
                                    ),
                                )
                                .put("required", JSONArray().put("query")),
                        ),
                ),
        )
        put(
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", ChatTools.WEB_FETCH)
                        .put("description", "打开一个 http / https 网页并读取正文。需要查看搜索结果里的具体页面时使用。")
                        .put(
                            "parameters",
                            JSONObject()
                                .put("type", "object")
                                .put(
                                    "properties",
                                    JSONObject().put(
                                        "url",
                                        JSONObject().put("type", "string").put("description", "完整网页地址"),
                                    ),
                                )
                                .put("required", JSONArray().put("url")),
                        ),
                ),
        )
    }

    // ───────────────────────── 小工具 ─────────────────────────

    private fun isActive(generation: Int): Boolean = this.generation.get() == generation

    private fun postIfActive(generation: Int, action: () -> Unit) {
        if (!isActive(generation)) return
        mainHandler.post { if (isActive(generation)) action() }
    }

    private fun readLimited(stream: InputStream?, limit: Int): String {
        if (stream == null) return ""
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val out = StringBuilder(minOf(limit, 8_192))
            val buffer = CharArray(4_096)
            var total = 0
            while (total < limit) {
                val read = reader.read(buffer)
                if (read < 0) break
                val take = minOf(read, limit - total)
                out.append(buffer, 0, take)
                total += take
            }
            return out.toString()
        }
    }

    private fun gatewayError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "无响应内容"
        runCatching {
            val json = JSONObject(trimmed)
            val message = json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
            if (message.isNotBlank() && message != "null") return message
        }
        return trimmed.take(300)
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is ConnectException -> "无法连接本机网关（127.0.0.1），请确认网关已启动"
        is SocketTimeoutException -> "网关响应超时，请稍后重试"
        is UnknownHostException -> "网关地址无法解析"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    /** org.json 对 JSON null 的 optString 会得到 "null"，统一成空串。 */
    private fun JSONObject.text(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key)
        return if (value == "null") "" else value
    }

    private class ToolCallAcc {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private class ToolCall(
        val id: String,
        val name: String,
        val arguments: String,
    )

    private class RoundOutcome(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ToolCall>,
        val finishReason: String?,
    )

    private companion object {
        const val MAX_TOOL_ROUNDS = 3
        const val MAX_TOOL_RESULT_CHARS = 12_000
        const val MAX_ERROR_CHARS = 4_000
        const val MAX_RESPONSE_CHARS = 400_000
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 180_000
    }
}
