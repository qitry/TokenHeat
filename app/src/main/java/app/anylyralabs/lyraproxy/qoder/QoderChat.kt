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
        val tools = request.optJSONArray("tools")?.let { normalizeTools(it) } ?: JSONArray()

        val recordId = UUID.randomUUID().toString().replace("-", "").take(16)
        val body = JSONObject().apply {
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
                put("enable_thinking", false)
            })
        }.toString()

        val path = "/api/v2/service/pro/sse/agent_chat_generation"
        val signed = CosySigner.sign(
            method = "POST",
            path = path,
            body = body,
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
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
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
                    relayFrames(upstream, writer, modelId.ifEmpty { model.id })
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
                        },
                    )
                    "tool" -> result.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", message.optString("tool_call_id"))
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

    private fun relayFrames(upstream: InputStream, writer: OutputStreamWriter, model: String) {
        val reader = upstream.bufferedReader(StandardCharsets.UTF_8)
        val id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
        val created = System.currentTimeMillis() / 1000

        emit(writer, chunk(id, created, model, JSONObject().put("role", "assistant"), null))
        var promptTokens = 0
        var completionTokens = 0
        var sawDone = false

        while (!sawDone) {
            val line = reader.readLine() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.substring(5).trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") break

            val envelope = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            val innerRaw = envelope.optString("body")
            if (innerRaw == "[DONE]") break
            if (innerRaw.isEmpty()) continue
            val inner = runCatching { JSONObject(innerRaw) }.getOrNull() ?: continue

            inner.optJSONObject("usage")?.let { usage ->
                promptTokens = usage.optInt("prompt_tokens", promptTokens)
                completionTokens = usage.optInt("completion_tokens", completionTokens)
            }

            val choices = inner.optJSONArray("choices") ?: continue
            if (choices.length() == 0) continue
            val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue

            val reasoning = delta.optString("reasoning_content")
            if (reasoning.isNotEmpty()) {
                emit(
                    writer,
                    chunk(
                        id = id,
                        created = created,
                        model = model,
                        delta = JSONObject().put("reasoning_content", reasoning),
                        finishReason = null,
                    ),
                )
            }
            val content = delta.optString("content")
            if (content.isNotEmpty()) {
                emit(
                    writer,
                    chunk(
                        id = id,
                        created = created,
                        model = model,
                        delta = JSONObject().put("content", content),
                        finishReason = null,
                    ),
                )
            }
        }

        // 收尾：先给 finish_reason，再单独发一条 usage，最后 [DONE]。
        emit(writer, chunk(id, created, model, JSONObject(), "stop"))
        val usage = JSONObject().apply {
            put("prompt_tokens", promptTokens)
            put("completion_tokens", completionTokens)
            put("total_tokens", promptTokens + completionTokens)
        }
        emit(
            writer,
            JSONObject().apply {
                put("id", id)
                put("object", "chat.completion.chunk")
                put("created", created)
                put("model", model)
                put("choices", JSONArray())
                put("usage", usage)
            }.toString(),
        )
        writer.write("data: [DONE]\n\n")
        writer.flush()
    }

    private fun chunk(
        id: String,
        created: Long,
        model: String,
        delta: JSONObject,
        finishReason: String?,
    ): String = JSONObject().apply {
        put("id", id)
        put("object", "chat.completion.chunk")
        put("created", created)
        put("model", model)
        put(
            "choices",
            JSONArray().put(
                JSONObject().apply {
                    put("index", 0)
                    put("delta", delta)
                    put("finish_reason", finishReason ?: JSONObject.NULL)
                },
            ),
        )
    }.toString()

    private fun emit(writer: OutputStreamWriter, payload: String) {
        writer.write("data: $payload\n\n")
        writer.flush()
    }
}
