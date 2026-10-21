// Copyright 2026, LyraProxy contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网络工具的统一结果。
 *
 * 注意：Exa 和 Firecrawl 都**没有免密钥模式**，所以「没配置 key」是正常状态而不是错误：
 * 用 [Unavailable] 让模型知道工具不可用，聊天继续；真正的网络 / 解析问题才用 [Failed]。
 */
sealed class ToolResult {
    data class Ok(val text: String, val sources: List<ChatSource>) : ToolResult()

    data class Unavailable(val reason: String) : ToolResult()

    data class Failed(val reason: String) : ToolResult()
}

/**
 * 模型可以调用的两个网络工具，只用 HttpURLConnection，不引入新依赖。
 *
 * - `web_search`：优先 Exa；Exa 没配 key 或失败时退回 Firecrawl；两家都没 key 时返回 Unavailable。
 * - `web_fetch`：直接抓取一个 http / https 页面，去掉脚本和标签后截断。
 *
 * 所有方法都会阻塞网络，必须在后台线程调用（[ChatEngine] 已经这样做了）。
 */
object ChatTools {
    const val WEB_SEARCH = "web_search"
    const val WEB_FETCH = "web_fetch"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RESPONSE_CHARS = 200_000
    private const val MAX_FETCH_CHARS = 6_000
    private const val MAX_SNIPPET_CHARS = 1_000
    private const val MAX_REDIRECTS = 5
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) LyraChat/1.0"

    /** 执行某个工具调用。[argumentsJson] 是模型给出的 JSON 字符串（可能为空）。 */
    fun execute(name: String, argumentsJson: String, exaKey: String, firecrawlKey: String): ToolResult {
        val args = runCatching { if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson) }
            .getOrElse { return ToolResult.Failed("工具参数不是合法 JSON") }
        return when (name) {
            WEB_SEARCH -> {
                val query = args.str("query")
                if (query.isBlank()) ToolResult.Failed("缺少搜索关键词 query") else search(query, exaKey, firecrawlKey)
            }

            WEB_FETCH -> {
                val url = args.str("url")
                if (url.isBlank()) ToolResult.Failed("缺少网页地址 url") else fetch(url)
            }

            else -> ToolResult.Failed("未知工具：$name")
        }
    }

    /**
     * 两家搜索的调度：有 key 的优先试，失败自动换另一家；都没配置时优雅降级。
     */
    fun search(query: String, exaKey: String, firecrawlKey: String): ToolResult {
        val exa = exaKey.trim()
        val firecrawl = firecrawlKey.trim()

        // 两家都提供 keyless 的托管 MCP，未配置 key 也能用（有额度限制）。
        val problems = ArrayList<String>()
        run {
            when (val result = exaSearch(query, exa)) {
                is ToolResult.Ok -> return result
                is ToolResult.Unavailable -> problems.add(result.reason)
                is ToolResult.Failed -> problems.add("Exa：${result.reason}")
            }
        }
        if (true) {
            when (val result = firecrawlSearch(query, firecrawl)) {
                is ToolResult.Ok -> return result
                is ToolResult.Unavailable -> problems.add(result.reason)
                is ToolResult.Failed -> problems.add("Firecrawl：${result.reason}")
            }
        }
        return if (problems.isEmpty()) {
            ToolResult.Unavailable("Exa / Firecrawl 暂时不可用")
        } else {
            ToolResult.Failed(problems.joinToString("；"))
        }
    }

    /**
     * Exa 搜索。优先走 keyless 托管 MCP（https://mcp.exa.ai/mcp，工具 web_search_exa），
     * 配置了 key 时把它作为 `x-api-key` 一起带上以使用自己的额度。
     */
    fun exaSearch(query: String, apiKey: String): ToolResult {
        val key = apiKey.trim()
        val headers = if (key.isEmpty()) emptyMap() else mapOf("x-api-key" to key)
        return try {
            val text = mcpToolCall(
                endpoint = EXA_MCP,
                tool = "web_search_exa",
                arguments = JSONObject().apply {
                    put("query", query)
                    put("numResults", 5)
                },
                headers = headers,
            )
            val sources = LINK_REGEX.findAll(text).map { it.value }
                .distinct().take(5)
                .map { ChatSource(url = it, title = it, domain = hostOf(it)) }
                .toList()
            ToolResult.Ok(text.take(MAX_SNIPPET_CHARS * 4), sources)
        } catch (e: Exception) {
            if (key.isNotEmpty()) {
                // MCP 不可用时退回 REST，保证有 key 的用户仍然能搜
                runCatching {
                    val body = JSONObject().apply {
                        put("query", query)
                        put("numResults", 5)
                        put("contents", JSONObject().put("text", JSONObject().put("maxCharacters", 800)))
                    }
                    parseExa(query, postJson("https://api.exa.ai/search", body, mapOf("x-api-key" to key)))
                }.getOrElse { ToolResult.Failed(friendlyError(e)) }
            } else {
                ToolResult.Failed(friendlyError(e))
            }
        }
    }

    /**
     * Firecrawl 搜索。keyless MCP：https://mcp.firecrawl.dev/v2/mcp，工具 firecrawl_search；
     * 配置 key 时用 Bearer 走同一端点（额度更高）。
     */
    fun firecrawlSearch(query: String, apiKey: String): ToolResult {
        val key = apiKey.trim()
        val headers = if (key.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $key")
        return try {
            val text = mcpToolCall(
                endpoint = FIRECRAWL_MCP,
                tool = "firecrawl_search",
                arguments = JSONObject().apply {
                    put("query", query)
                    put("limit", 3)
                },
                headers = headers,
            )
            val sources = LINK_REGEX.findAll(text).map { it.value }
                .distinct().take(3)
                .map { ChatSource(url = it, title = it, domain = hostOf(it)) }
                .toList()
            ToolResult.Ok(text.take(MAX_SNIPPET_CHARS * 4), sources)
        } catch (e: Exception) {
            if (key.isNotEmpty()) {
                runCatching {
                    val body = JSONObject().apply {
                        put("query", query)
                        put("limit", 3)
                        put("scrapeOptions", JSONObject().put("formats", JSONArray().put("markdown")))
                    }
                    parseFirecrawl(
                        query,
                        postJson("https://api.firecrawl.dev/v1/search", body, mapOf("Authorization" to "Bearer $key")),
                    )
                }.getOrElse { ToolResult.Failed(friendlyError(e)) }
            } else {
                ToolResult.Failed(friendlyError(e))
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 托管 MCP（Streamable HTTP，JSON-RPC 2.0）
    // ------------------------------------------------------------------ //

    private const val EXA_MCP = "https://mcp.exa.ai/mcp"
    private const val FIRECRAWL_MCP = "https://mcp.firecrawl.dev/v2/mcp"
    private val LINK_REGEX = Regex("https?://[^\\s)\"'<>]+")

    /**
     * 一次完整的 MCP 工具调用：initialize → notifications/initialized → tools/call。
     * 返回结果里的文本内容；失败抛异常，由调用方降级。
     */
    private fun mcpToolCall(
        endpoint: String,
        tool: String,
        arguments: JSONObject,
        headers: Map<String, String>,
    ): String {
        val base = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/event-stream",
        ) + headers

        val init = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
            put(
                "params",
                JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject())
                    put(
                        "clientInfo",
                        JSONObject().apply {
                            put("name", "lyra-chat")
                            put("version", "1.0")
                        },
                    )
                },
            )
        }
        val (_, session) = postJsonFull(endpoint, init, base)

        val sessionHeader = session?.let { mapOf("Mcp-Session-Id" to it) } ?: emptyMap()
        runCatching {
            postJsonFull(
                endpoint,
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                },
                base + sessionHeader,
            )
        }

        val call = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/call")
            put(
                "params",
                JSONObject().apply {
                    put("name", tool)
                    put("arguments", arguments)
                },
            )
        }
        val (body, _) = postJsonFull(endpoint, call, base + sessionHeader)
        val json = extractJson(body)
        val result = json.optJSONObject("result")
        if (json.has("error")) {
            throw IllegalStateException(json.optJSONObject("error")?.optString("message") ?: "MCP error")
        }
        val content = result?.optJSONArray("content")
        val texts = ArrayList<String>()
        if (content != null) {
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                if (part.optString("type") == "text") texts.add(part.optString("text"))
            }
        }
        if (texts.isEmpty()) {
            // 有些服务把结构化结果放在 structuredContent 里
            result?.optJSONObject("structuredContent")?.let { texts.add(it.toString()) }
        }
        if (texts.isEmpty()) throw IllegalStateException("MCP 返回为空")
        return texts.joinToString("\n")
    }

    /** 响应可能是普通 JSON，也可能是 SSE（data: {...}）。 */
    private fun extractJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        runCatching { return JSONObject(trimmed) }
        val payloads = trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
            .toList()
        for (payload in payloads.asReversed()) {
            runCatching { return JSONObject(payload) }
        }
        throw IllegalStateException("MCP 响应不是 JSON")
    }

    /** 抓取网页正文：GET，去掉 script / style / 标签，截断到 6000 字。 */
    fun fetch(url: String): ToolResult {
        var current = url.trim()
        var redirects = 0

        while (true) {
            if (!current.startsWith("http://", ignoreCase = true) && !current.startsWith("https://", ignoreCase = true)) {
                return ToolResult.Failed("只支持 http / https 链接")
            }
            val host = runCatching { URL(current).host }.getOrNull().orEmpty()
            if (host.isBlank()) return ToolResult.Failed("网页地址无效")
            // 防止模型抓取本机网关或内网服务（SSRF）：网关就在 127.0.0.1 上，不能让它被网页里的提示词诱导访问
            if (isPrivateHost(host)) return ToolResult.Failed("出于安全考虑，不能抓取本机或内网地址")

            val connection = try {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    // 手动跟重定向，每一跳都重新做内网检查
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                    setRequestProperty("Accept-Encoding", "identity")
                }
            } catch (e: Exception) {
                return ToolResult.Failed(friendlyError(e))
            }

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) return ToolResult.Failed("网页返回了空重定向")
                    if (++redirects > MAX_REDIRECTS) return ToolResult.Failed("网页重定向次数过多")
                    current = runCatching { URL(URL(current), location).toString() }.getOrElse {
                        return ToolResult.Failed("重定向地址无效")
                    }
                    continue
                }

                val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = readLimited(stream, MAX_RESPONSE_CHARS)
                if (code !in 200..299) {
                    return ToolResult.Failed("HTTP $code：${errorText(raw)}")
                }

                val isHtml = connection.contentType?.contains("html", ignoreCase = true) == true ||
                    raw.trimStart().startsWith("<")
                val text = if (isHtml) htmlToText(raw) else raw.trim()
                if (text.isBlank()) return ToolResult.Failed("页面没有可读文本")

                val clipped = if (text.length > MAX_FETCH_CHARS) {
                    text.take(MAX_FETCH_CHARS) + "\n…（内容已截断）"
                } else {
                    text
                }
                return ToolResult.Ok(
                    text = "网页内容：$current\n\n$clipped",
                    sources = listOf(ChatSource.of(ChatSource.domainOf(current), current)),
                )
            } catch (e: Exception) {
                return ToolResult.Failed(friendlyError(e))
            } finally {
                connection.disconnect()
            }
        }
    }

    // ───────────────────────── 解析 ─────────────────────────

    private fun parseExa(query: String, raw: String): ToolResult {
        val root = JSONObject(raw)
        val results = root.optJSONArray("results") ?: JSONArray()
        val sources = ArrayList<ChatSource>()
        val text = StringBuilder("Exa 搜索：").append(query).append('\n')

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val url = item.str("url")
            if (url.isBlank()) continue
            val title = item.str("title").ifBlank { ChatSource.domainOf(url) }
            val snippet = item.str("text").ifBlank { item.str("summary") }
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_SNIPPET_CHARS)
            text.append("\n[${sources.size + 1}] ").append(title).append('\n')
            text.append(url).append('\n')
            if (snippet.isNotBlank()) text.append(snippet).append('\n')
            sources.add(ChatSource.of(title, url))
        }

        return if (sources.isEmpty()) {
            ToolResult.Failed("Exa 没有返回结果")
        } else {
            ToolResult.Ok(text.toString().trim(), sources)
        }
    }

    private fun parseFirecrawl(query: String, raw: String): ToolResult {
        val root = JSONObject(raw)
        if (root.has("success") && !root.optBoolean("success", true)) {
            return ToolResult.Failed(errorText(raw))
        }
        // 不同版本把结果放在 data 数组或 data.web 里
        val results = root.optJSONArray("data")
            ?: root.optJSONObject("data")?.optJSONArray("web")
            ?: JSONArray()

        val sources = ArrayList<ChatSource>()
        val text = StringBuilder("Firecrawl 搜索：").append(query).append('\n')

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val url = item.str("url")
            if (url.isBlank()) continue
            val title = item.str("title").ifBlank { ChatSource.domainOf(url) }
            val snippet = item.str("markdown")
                .ifBlank { item.str("description") }
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_SNIPPET_CHARS)
            text.append("\n[${sources.size + 1}] ").append(title).append('\n')
            text.append(url).append('\n')
            if (snippet.isNotBlank()) text.append(snippet).append('\n')
            sources.add(ChatSource.of(title, url))
        }

        return if (sources.isEmpty()) {
            ToolResult.Failed("Firecrawl 没有返回结果")
        } else {
            ToolResult.Ok(text.toString().trim(), sources)
        }
    }

    // ───────────────────────── HTTP ─────────────────────────

    /** 和 [postJson] 相同，但把响应头里的 MCP 会话 id 一并返回。 */
    private fun postJsonFull(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
    ): Pair<String, String?> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val session = connection.getHeaderField("Mcp-Session-Id")
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = readLimited(stream, MAX_RESPONSE_CHARS)
            if (code !in 200..299) throw IOException("HTTP $code：${errorText(raw)}")
            raw to session
        } finally {
            connection.disconnect()
        }
    }

    /** 取 URL 的域名，失败时退回原串。 */
    private fun hostOf(url: String): String = runCatching { URL(url).host }.getOrDefault(url)

    private fun postJson(url: String, body: JSONObject, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = readLimited(stream, MAX_RESPONSE_CHARS)
            if (code !in 200..299) throw IOException("HTTP $code：${errorText(raw)}")
            raw
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream?, limit: Int): String {
        if (stream == null) return ""
        return InputStreamReader(stream, Charsets.UTF_8).use { reader -> readLimited(reader, limit) }
    }

    private fun readLimited(reader: Reader, limit: Int): String {
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

    /** 错误响应优先取 `error.message`，其次取 `error` 字符串，再退回正文片段。 */
    private fun errorText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "无响应内容"
        runCatching {
            val json = JSONObject(trimmed)
            val message = json.optJSONObject("error")?.str("message")
                ?: json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("message").takeIf { it.isNotBlank() && it != "null" }
            if (!message.isNullOrBlank()) return message
        }
        return trimmed.take(240)
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "请求超时，请稍后再试"
        is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络"
        is java.net.ConnectException -> "无法连接服务器，请检查网络"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    /** 只做字面量判断，挡住 localhost / 内网网段 / 云元数据地址；不做 DNS 解析（代价高且可能误伤）。 */
    private fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h.endsWith(".local") || h == "::1" || h == "0.0.0.0") return true
        val parts = h.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        val a = octets[0]
        val b = octets[1]
        return a == 0 || a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254)
    }

    // ───────────────────────── HTML ─────────────────────────

    private val scriptOrStyle = Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>")
    private val htmlComment = Regex("(?s)<!--.*?-->")
    private val anyTag = Regex("(?s)<[^>]+>")
    private val whitespace = Regex("[\\t\\x0B\\f\\r ]+")
    private val manyNewlines = Regex("\\n{3,}")

    private fun htmlToText(html: String): String {
        var text = html
            .replace(htmlComment, " ")
            .replace(scriptOrStyle, " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|section|article)>"), "\n")
            .replace(anyTag, " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        return text
            .replace(whitespace, " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(manyNewlines, "\n\n")
            .trim()
    }

    /** org.json 的 optString 会把 JSON null 读成 "null"，这里统一成空串。 */
    private fun JSONObject.str(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key).trim()
        return if (value == "null") "" else value
    }
}
