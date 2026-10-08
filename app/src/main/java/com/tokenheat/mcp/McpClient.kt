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
    private const val EXA_SEARCH_TOOL_NAME = "exa_search"

    /**
     * Builds OpenAI compatible tools JSONArray from enabled remote MCP servers
     * and built-in Exa AI search engine.
     */
    suspend fun getAvailableTools(
        servers: List<McpServerConfig>,
        exaApiKey: String,
    ): JSONArray = withContext(Dispatchers.IO) {
        val toolsArray = JSONArray()

        // 1. Built-in Exa search tool (if API key configured)
        if (exaApiKey.isNotBlank()) {
            val exaTool = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", EXA_SEARCH_TOOL_NAME)
                    put("description", "使用 Exa 智能搜索引擎实时检索全网最新网页、技术文档、新闻资讯及事实内容")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        val properties = JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "需要搜索的关键词或自然语言提问")
                            })
                            put("num_results", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回结果数量，默认5")
                            })
                        }
                        put("properties", properties)
                        put("required", JSONArray().put("query"))
                    })
                })
            }
            toolsArray.put(exaTool)
        }

        // 2. Discover tools from enabled remote MCP servers
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
        if (toolName == EXA_SEARCH_TOOL_NAME) {
            val args = runCatching { JSONObject(argumentsJson) }.getOrDefault(JSONObject())
            val query = args.optString("query")
            val count = args.optInt("num_results", 5).coerceIn(1, 10)
            return@withContext executeExaSearch(exaApiKey, query, count)
        }

        // Search through enabled servers for matching tool
        for (server in servers.filter { it.enabled && it.url.isNotBlank() }) {
            val res = runCatching {
                callRemoteTool(server, toolName, argumentsJson)
            }.getOrNull()
            if (res != null) return@withContext res
        }

        "工具 [$toolName] 执行失败：未找到对应的可用 MCP 服务"
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
