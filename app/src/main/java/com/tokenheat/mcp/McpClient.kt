package com.tokenheat.mcp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/** Supported transport protocols for remote Model Context Protocol (MCP) servers. */
enum class McpProtocol(val label: String) {
    STREAMABLE_HTTP("Streamable HTTP"),
    SSE("Server-Sent Events (SSE)"),
    WEBSOCKET("WebSocket (WS/WSS)"),
}

/** Configuration entity for a remote MCP server. */
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: McpProtocol = McpProtocol.STREAMABLE_HTTP,
    val url: String,
    val authHeader: String = "", // e.g. "Authorization: Bearer <token>" or "x-api-key: <key>"
    val enabled: Boolean = true,
)

/** Unified MCP & Exa AI Engine coordinator. */
object McpManager {
    private const val TAG = "McpManager"
    private const val TOOL_WEB_FETCH = "web_fetch"
    private const val TOOL_WEB_SEARCH = "web_search"
    private const val TOOL_GET_TIME = "get_current_time"
    private const val TOOL_CALCULATOR = "calculator"

    /**
     * Builds OpenAI compatible tools JSONArray from enabled remote MCP servers
     * and built-in essential tools (web_fetch, web_search, current_time, calculator).
     */
    suspend fun getAvailableTools(
        servers: List<McpServerConfig>,
        exaApiKey: String,
    ): JSONArray = withContext(Dispatchers.IO) {
        val toolsArray = JSONArray()

        // 1. Built-in web_fetch tool (extract content from any URL)
        toolsArray.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_WEB_FETCH)
                put("description", "抓取并读取指定网址 (URL) 的正文内容，自动过滤 HTML 标签并提取网页标题与文本段落")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val properties = JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "需要访问抓取的完整网页 URL (http 或 https 开头)")
                        })
                        put("max_length", JSONObject().apply {
                            put("type", "integer")
                            put("description", "返回文本的最大字数限制，默认 4000")
                        })
                    }
                    put("properties", properties)
                    put("required", JSONArray().put("url"))
                })
            })
        })

        // 2. Built-in web_search tool (Exa neural search or DuckDuckGo fallback)
        toolsArray.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_WEB_SEARCH)
                put("description", "在互联网上检索最新信息、实时资讯、新闻动态或参考文档")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val properties = JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "要检索的搜索关键词或自然语言提问")
                        })
                        put("num_results", JSONObject().apply {
                            put("type", "integer")
                            put("description", "返回结果数量，默认 5 条")
                        })
                    }
                    put("properties", properties)
                    put("required", JSONArray().put("query"))
                })
            })
        })

        // 3. Built-in get_current_time tool
        toolsArray.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_GET_TIME)
                put("description", "获取系统当前的精确日期、时间、星期几以及所在时区")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // 4. Built-in calculator tool
        toolsArray.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_CALCULATOR)
                put("description", "计算数学表达式（支持基础四则运算 +、-、*、/、^、括号等）")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val properties = JSONObject().apply {
                        put("expression", JSONObject().apply {
                            put("type", "string")
                            put("description", "数学算式，例如 '128 * 4 + (50 / 2)'")
                        })
                    }
                    put("properties", properties)
                    put("required", JSONArray().put("expression"))
                })
            })
        })

        // 5. Discover tools from enabled remote MCP servers
        servers.filter { it.enabled && it.url.isNotBlank() }.forEach { server ->
            runCatching {
                val remoteTools = listRemoteTools(server)
                for (tool in remoteTools) {
                    toolsArray.put(tool)
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to list tools from MCP server ${server.name}: ${e.message}")
            }
        }

        toolsArray
    }

    /**
     * Executes a tool invocation dispatched by the AI model.
     */
    suspend fun executeTool(
        toolName: String,
        argumentsJson: String,
        servers: List<McpServerConfig>,
        exaApiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val args = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())

        when (toolName) {
            TOOL_WEB_FETCH -> {
                val url = args.optString("url")
                val maxLength = args.optInt("max_length", 4000).coerceIn(500, 15000)
                return@withContext executeWebFetch(url, maxLength)
            }
            TOOL_WEB_SEARCH, "exa_search" -> {
                val query = args.optString("query")
                val count = args.optInt("num_results", 5).coerceIn(1, 10)
                return@withContext executeWebSearch(exaApiKey, query, count)
            }
            TOOL_GET_TIME -> {
                return@withContext executeGetCurrentTime()
            }
            TOOL_CALCULATOR -> {
                val expr = args.optString("expression")
                return@withContext executeCalculator(expr)
            }
        }

        // Search through enabled servers for matching tool
        for (server in servers.filter { it.enabled && it.url.isNotBlank() }) {
            val res = runCatching {
                callRemoteTool(server, toolName, argumentsJson)
            }.getOrNull()
            if (res != null) return@withContext res
        }

        "工具 [$toolName] 执行失败：未找到对应的可用 MCP 服务或内置工具"
    }

    /**
     * Executes web search. Prefers Exa AI if configured, otherwise falls back
     * to zero-config public web search retrieval.
     */
    private fun executeWebSearch(apiKey: String, query: String, numResults: Int): String {
        if (query.isBlank()) return "搜索错误：搜索关键词为空"
        if (apiKey.isNotBlank()) {
            return executeExaSearch(apiKey, query, numResults)
        }
        return executePublicWebSearch(query, numResults)
    }

    /**
     * Fetches and cleans textual content from a webpage URL.
     */
    private fun executeWebFetch(urlStr: String, maxLength: Int): String {
        if (urlStr.isBlank()) return "WebFetch 错误：URL 不能为空"
        val normalizedUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            "https://$urlStr"
        } else {
            urlStr
        }

        return try {
            val url = URL(normalizedUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            if (code !in 200..299) {
                return "网页访问失败 ($code): ${conn.responseMessage}"
            }

            val contentType = conn.contentType ?: ""
            val rawHtml = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val sb = java.lang.StringBuilder()
                val buf = CharArray(4096)
                var totalChars = 0
                val maxCharsToRead = 500_000
                while (true) {
                    val read = reader.read(buf)
                    if (read <= 0) break
                    sb.append(buf, 0, read)
                    totalChars += read
                    if (totalChars >= maxCharsToRead) break
                }
                sb.toString()
            }

            val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            val titleMatch = titleRegex.find(rawHtml)
            val pageTitle = titleMatch?.groupValues?.get(1)?.trim()?.replace("&nbsp;", " ") ?: "无标题"

            var cleaned = rawHtml
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<noscript[^>]*>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)<(br|p|div|li|tr|h[1-6])[^>]*>"), "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")

            // Collapse multiple blank lines
            cleaned = cleaned.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            val truncated = if (cleaned.length > maxLength) {
                cleaned.take(maxLength) + "\n\n... (内容过长已截断，共 ${cleaned.length} 字符)"
            } else {
                cleaned
            }

            buildString {
                appendLine("[网页抓取结果]")
                appendLine("标题: $pageTitle")
                appendLine("URL: $normalizedUrl")
                appendLine("正文内容:")
                appendLine(truncated.ifBlank { "未提取到正文文本内容。" })
            }
        } catch (e: Exception) {
            "网页抓取异常 (${e.javaClass.simpleName}): ${e.message ?: "连接失败"}"
        }
    }

    /**
     * Zero-config public search fallback when Exa API key is not provided.
     */
    private fun executePublicWebSearch(query: String, numResults: Int): String {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.doOutput = true

            conn.outputStream.use { it.write("q=$encoded".toByteArray(StandardCharsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                val html = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val resultRegex = Regex("<a class=\"result__snippet[^>]*href=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</a>")
                val titleRegex = Regex("<a class=\"result__url\"[^>]*>([\\s\\S]*?)</a>")

                val matches = resultRegex.findAll(html).toList()
                if (matches.isNotEmpty()) {
                    val sb = StringBuilder()
                    sb.appendLine("联网搜索检索结果 (关键词: \"$query\"):")
                    matches.take(numResults).forEachIndexed { index, m ->
                        val itemSnippet = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                        val itemUrl = m.groupValues[1]
                        sb.appendLine("${index + 1}. 链接: $itemUrl")
                        sb.appendLine("   摘要: $itemSnippet")
                        sb.appendLine()
                    }
                    return sb.toString()
                }
            }

            // Fallback: DDG Instant Answer API
            val apiUrl = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1")
            val apiConn = apiUrl.openConnection() as HttpURLConnection
            apiConn.connectTimeout = 10_000
            apiConn.readTimeout = 15_000
            val resp = apiConn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val json = JSONObject(resp)
            val heading = json.optString("Heading")
            val abstractText = json.optString("AbstractText")
            val related = json.optJSONArray("RelatedTopics")

            val sb = StringBuilder()
            sb.appendLine("联网检索结果 (关键词: \"$query\"):")
            if (abstractText.isNotBlank()) {
                sb.appendLine("【概要】$heading: $abstractText\n")
            }
            if (related != null) {
                var count = 0
                for (i in 0 until related.length()) {
                    if (count >= numResults) break
                    val item = related.optJSONObject(i) ?: continue
                    val text = item.optString("Text")
                    val firstUrl = item.optString("FirstURL")
                    if (text.isNotBlank()) {
                        count++
                        sb.appendLine("$count. $text")
                        if (firstUrl.isNotBlank()) sb.appendLine("   来源: $firstUrl")
                        sb.appendLine()
                    }
                }
            }
            if (sb.length > 50) sb.toString() else "未检索到关于 \"$query\" 的即时信息，建议使用更具体的关键词。"
        } catch (e: Exception) {
            "公共网络搜索异常: ${e.message ?: "网络超时"} (建议在设置中配置 Exa API Key 以获得更稳定的专业神经网络搜索)"
        }
    }

    /**
     * Returns current accurate date, time, weekday and timezone.
     */
    private fun executeGetCurrentTime(): String {
        val cal = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss EEEE", java.util.Locale.CHINA)
        val tz = java.util.TimeZone.getDefault()
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)

        return buildString {
            appendLine("【当前系统时间】")
            appendLine("本地时间: ${sdf.format(cal.time)}")
            appendLine("标准 ISO 时间: ${isoFormat.format(cal.time)}")
            appendLine("时区: ${tz.id} (${tz.getDisplayName(false, java.util.TimeZone.SHORT)})")
            appendLine("毫秒时间戳: ${System.currentTimeMillis()}")
        }
    }

    /**
     * Evaluates a mathematical expression safely.
     */
    private fun executeCalculator(expr: String): String {
        if (expr.isBlank()) return "计算器错误：表达式为空"
        return try {
            val sanitized = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
            val result = evaluateMathExpression(sanitized)
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.6f", result).trimEnd('0').trimEnd('.')
            }
            "计算结果: $expr = $formatted"
        } catch (e: Exception) {
            "计算器错误: 无法解析表达式 '$expr' (${e.message})"
        }
    }

    private fun evaluateMathExpression(expr: String): Double {
        return ExpressionParser(expr).parse()
    }

    private class ExpressionParser(private val expr: String) {
        private var pos = -1
        private var ch = -1

        private fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < expr.length) throw IllegalArgumentException("意外字符: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("除数不能为零")
                        x /= divisor
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("意外字符: " + ch.toChar())
            }

            if (eat('^'.code)) x = Math.pow(x, parseFactor())
            return x
        }
    }

    /**
     * Directly calls the Exa AI REST API to retrieve real-time web search results.
     */
    private fun executeExaSearch(apiKey: String, query: String, numResults: Int): String {
        if (apiKey.isBlank()) return "Exa 搜索错误：未配置 Exa API Key"
        if (query.isBlank()) return "Exa 搜索错误：搜索关键词为空"

        try {
            val url = URL("https://api.exa.ai/search")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-api-key", apiKey.trim())
            conn.connectTimeout = 12_000
            conn.readTimeout = 25_000
            conn.doOutput = true

            val reqBody = JSONObject().apply {
                put("query", query)
                put("num_results", numResults)
                put("use_autoprompt", true)
                put("type", "neural")
            }

            conn.outputStream.use { it.write(reqBody.toString().toByteArray(StandardCharsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return "Exa 搜索请求失败 ($code): $err"
            }

            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            val results = json.optJSONArray("results") ?: return "未找到关于 \"$query\" 的相关结果"

            val sb = StringBuilder()
            sb.append("Exa 联网搜索检索结果 (关键词: \"$query\"):\n\n")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "网页结果")
                val itemUrl = item.optString("url")
                val text = item.optString("text").take(400)
                val published = item.optString("published_date")

                sb.append("${i + 1}. **$title**\n")
                if (published.isNotBlank()) sb.append("   - 发布时间: $published\n")
                sb.append("   - 来源链接: $itemUrl\n")
                if (text.isNotBlank()) sb.append("   - 摘要: $text...\n")
                sb.append("\n")
            }

            return sb.toString()
        } catch (e: Exception) {
            return "Exa 联网检索异常: ${e.message ?: "网络超时"}"
        }
    }

    /**
     * Lists tools exposed by a remote MCP server conforming to MCP protocol specification.
     */
    private fun listRemoteTools(server: McpServerConfig): List<JSONObject> {
        val rpcRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val responseStr = sendMcpRpc(server, rpcRequest.toString()) ?: return emptyList()
        val json = JSONObject(responseStr)
        val result = json.optJSONObject("result") ?: return emptyList()
        val toolsArray = result.optJSONArray("tools") ?: return emptyList()

        val list = mutableListOf<JSONObject>()
        for (i in 0 until toolsArray.length()) {
            val toolObj = toolsArray.getJSONObject(i)
            val name = toolObj.optString("name")
            val desc = toolObj.optString("description")
            val schema = toolObj.optJSONObject("inputSchema") ?: JSONObject()

            val openAiFunction = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", schema)
                })
            }
            list.add(openAiFunction)
        }
        return list
    }

    /**
     * Calls a specific tool on the remote MCP server conforming to MCP protocol specification.
     */
    private fun callRemoteTool(server: McpServerConfig, toolName: String, argumentsJson: String): String? {
        val argsObj = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
        val rpcRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", argsObj)
            })
        }

        val responseStr = sendMcpRpc(server, rpcRequest.toString()) ?: return null
        val json = JSONObject(responseStr)
        val result = json.optJSONObject("result")
        if (result != null) {
            val contentArr = result.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until contentArr.length()) {
                    val c = contentArr.getJSONObject(i)
                    sb.append(c.optString("text", "")).append("\n")
                }
                return sb.toString().trim()
            }
            return result.toString()
        }
        val error = json.optJSONObject("error")
        if (error != null) {
            return "MCP 工具调用错误: ${error.optString("message", error.toString())}"
        }
        return responseStr
    }

    /**
     * Transports JSON-RPC 2.0 messages across Streamable HTTP, SSE, or WebSocket.
     */
    private fun sendMcpRpc(server: McpServerConfig, payload: String): String? {
        return when (server.protocol) {
            McpProtocol.STREAMABLE_HTTP -> sendViaHttp(server.url, payload, server.authHeader)
            McpProtocol.SSE -> sendViaSse(server.url, payload, server.authHeader)
            McpProtocol.WEBSOCKET -> sendViaWebSocket(server.url, payload, server.authHeader)
        }
    }

    /** Streamable HTTP transport (JSON-RPC POST). */
    private fun sendViaHttp(endpointUrl: String, jsonRpc: String, authHeader: String): String? {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        applyAuthHeader(conn, authHeader)
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.doOutput = true

        conn.outputStream.use { it.write(jsonRpc.toByteArray(StandardCharsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) return null
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** Server-Sent Events (SSE) legacy transport. */
    private fun sendViaSse(sseUrl: String, jsonRpc: String, authHeader: String): String? {
        // For MCP SSE transport, POST messages are usually sent to the message endpoint
        // or directly to the target URL.
        return sendViaHttp(sseUrl, jsonRpc, authHeader)
    }

    /** Lightweight native RFC 6455 WebSocket transport. */
    private fun sendViaWebSocket(wsUrl: String, jsonRpc: String, authHeader: String): String? {
        return runCatching {
            val uri = URI(wsUrl)
            val isSecure = uri.scheme.equals("wss", ignoreCase = true)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else if (isSecure) 443 else 80
            val path = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath

            val rawSocket = if (isSecure) SSLSocketFactory.getDefault().createSocket(host, port) else Socket(host, port)
            rawSocket.soTimeout = 15_000

            val out = rawSocket.getOutputStream()
            val input = rawSocket.getInputStream()

            // 1. Handshake
            val keyBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val key = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
            val request = StringBuilder()
                .append("GET $path HTTP/1.1\r\n")
                .append("Host: $host\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: $key\r\n")
                .append("Sec-WebSocket-Version: 13\r\n")
            if (authHeader.isNotBlank()) {
                request.append("$authHeader\r\n")
            }
            request.append("\r\n")

            out.write(request.toString().toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            // Read Handshake response
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.US_ASCII))
            val statusLine = reader.readLine() ?: return null
            if (!statusLine.contains("101")) {
                rawSocket.close()
                return null
            }
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            // 2. Send WebSocket text frame (masked)
            val payloadBytes = jsonRpc.toByteArray(StandardCharsets.UTF_8)
            val mask = ByteArray(4).apply { SecureRandom().nextBytes(this) }
            out.write(0x81) // Text frame opcode
            if (payloadBytes.size <= 125) {
                out.write(payloadBytes.size or 0x80)
            } else {
                out.write(126 or 0x80)
                out.write((payloadBytes.size shr 8) and 0xFF)
                out.write(payloadBytes.size and 0xFF)
            }
            out.write(mask)
            val masked = ByteArray(payloadBytes.size) { i -> (payloadBytes[i].toInt() xor mask[i % 4].toInt()).toByte() }
            out.write(masked)
            out.flush()

            // 3. Read single WebSocket frame response
            val b0 = input.read()
            if (b0 < 0) return null
            val b1 = input.read()
            if (b1 < 0) return null
            var len = b1 and 0x7F
            if (len == 126) {
                val h = input.read()
                val l = input.read()
                len = (h shl 8) or l
            }
            val resBuffer = ByteArray(len)
            var totalRead = 0
            while (totalRead < len) {
                val r = input.read(resBuffer, totalRead, len - totalRead)
                if (r < 0) break
                totalRead += r
            }

            rawSocket.close()
            String(resBuffer, 0, totalRead, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun applyAuthHeader(conn: HttpURLConnection, authHeader: String) {
        if (authHeader.isBlank()) return
        val colon = authHeader.indexOf(':')
        if (colon > 0) {
            val key = authHeader.substring(0, colon).trim()
            val value = authHeader.substring(colon + 1).trim()
            conn.setRequestProperty(key, value)
        } else {
            conn.setRequestProperty("Authorization", authHeader.trim())
        }
    }
}
