package app.anylyralabs.lyraproxy.bridge

import app.anylyralabs.lyraproxy.proto.Credential
import app.anylyralabs.lyraproxy.proto.UpstreamClient
import app.anylyralabs.lyraproxy.proto.Wire
import app.anylyralabs.lyraproxy.qoder.QoderAccount
import app.anylyralabs.lyraproxy.qoder.QoderChat
import app.anylyralabs.lyraproxy.qoder.QoderModel
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A small OpenAI-compatible endpoint on loopback.
 *
 * Requests and responses are handled as raw bytes: a request body is declared
 * in bytes and JSON may contain multi-byte characters, so decoding into
 * characters before reading would desynchronise the stream.
 *
 * Only loopback is served and every request must present the shared secret,
 * because the endpoint holds a credential.
 */
class BridgeServer(
    private val port: Int,
    private val secret: String,
    private val credential: () -> Credential?,
    private val models: () -> List<String>,
    private val onCall: (CallRecord) -> Unit = {},
    /**
     * 解析请求携带的密钥。
     *
     * 返回 null 表示不认这个密钥，请求被拒；返回 [AuthContext] 表示放行，
     * 并带上该密钥的模型白名单与预算约束。默认实现只认共享密钥，
     * 保持既有行为，便于单独使用。
     */
    private val authenticate: (String) -> AuthContext? = { presented ->
        if (presented == secret) AuthContext.DEFAULT else null
    },
    /** 调用结束后按密钥记账。 */
    private val onKeyUsage: (String, Double) -> Unit = { _, _ -> },
    /** 是否把请求转给 Qoder 而不是 WorkBuddy。 */
    private val useQoder: () -> Boolean = { false },
    /** 当前 Qoder 账号。 */
    private val qoderAccount: () -> QoderAccount? = { null },
    /** Qoder 模型目录，用于模型名映射。 */
    private val qoderModels: () -> List<QoderModel> = { emptyList() },
) {

    /** 通过鉴权后携带的约束。 */
    data class AuthContext(
        val keyId: String,
        val allowedModels: List<String>,
        val budgetRemaining: Double,
    ) {
        fun modelAllowed(model: String): Boolean =
            allowedModels.isEmpty() || allowedModels.any { it.equals(model, ignoreCase = true) }

        fun budgetAllows(): Boolean = budgetRemaining > 0.0

        companion object {
            /** 共享密钥：不限制模型与预算。 */
            val DEFAULT = AuthContext("shared", emptyList(), Double.MAX_VALUE)
        }
    }

    /** Host values a loopback-addressed request may carry. */
    private val loopbackHosts = setOf("127.0.0.1", "localhost", "[::1]")

    /** Upper bound on a request body, so one client cannot exhaust memory. */
    private val requestBodyLimit = 64 * 1024 * 1024

    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private val client = UpstreamClient()
    private var server: ServerSocket? = null

    fun start() {
        if (running.getAndSet(true)) return
        val socket = try {
            ServerSocket(port, 64, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "cannot bind 127.0.0.1:$port", e)
            running.set(false)
            return
        }
        server = socket
        android.util.Log.i(TAG, "bridge listening on 127.0.0.1:$port")
        Thread {
            while (running.get()) {
                val accepted = try {
                    socket.accept()
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    if (running.get()) android.util.Log.w(TAG, "accept failed", e)
                    continue
                }
                pool.submit { handle(accepted) }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        try {
            socket.soTimeout = 30_000
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (length > 0) {
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(buffer, read, length - read)
                    if (n < 0) break
                    read += n
                }
                String(buffer, 0, read, StandardCharsets.UTF_8)
            } else {
                ""
            }

            BridgeStatus.recordRequest("${requestLine.substringBefore(" ")} ${requestLine.split(" ").getOrNull(1).orEmpty()}")

            val header = headers["authorization"].orEmpty().trim()
            val presented = if (header.startsWith("Bearer ", ignoreCase = true)) header.substring(7).trim() else header
            val auth = authenticate(presented)
            if (auth == null) {
                sendJson(output, 401, """{"error":{"message":"invalid api key","type":"unauthorized"}}""")
                return
            }
            if (!auth.budgetAllows()) {
                sendJson(
                    output,
                    402,
                    """{"error":{"message":"api key budget exhausted","type":"budget_exhausted"}}""",
                )
                return
            }

            // Inbound hardening. The loopback bind alone is not a trust
            // boundary: any local process, or a page that re-resolves its own
            // domain to 127.0.0.1, can reach the port. A browser-originated
            // request must therefore name loopback in both Host and Origin, and
            // a body must be JSON.
            if (!hostIsLoopback(headers["host"])) {
                sendJson(output, 403, """{"error":{"message":"host not allowed","type":"host_not_allowed"}}""")
                return
            }
            if (!originIsLoopback(headers["origin"])) {
                sendJson(output, 403, """{"error":{"message":"origin not allowed","type":"origin_not_allowed"}}""")
                return
            }
            if (length > requestBodyLimit) {
                sendJson(output, 413, """{"error":{"message":"request too large","type":"too_large"}}""")
                return
            }

            when {
                requestLine.startsWith("GET /healthz") -> {
                    val region = credential()?.region?.name ?: "none"
                    BridgeStatus.region = region
                    sendJson(output, 200, """{"ok":true,"region":"$region"}""")
                }
                requestLine.startsWith("GET /v1/models") -> {
                    // 只列出该密钥可用的模型，避免客户端选到一个必然被拒的模型。
                    val source = if (useQoder()) qoderModels().map { it.id } else models()
                    val allowed = source.filter { auth.modelAllowed(it) }
                    val list = allowed.joinToString(",") {
                        """{"id":"$it","object":"model","owned_by":"workbuddy"}"""
                    }
                    sendJson(output, 200, """{"object":"list","data":[$list]}""")
                }
                requestLine.startsWith("POST /v1/chat/completions") ->
                    chat(output, body, headers["content-type"], auth)
                else -> sendJson(output, 404, """{"error":{"message":"not found","type":"not_found"}}""")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "request failed", e)
            runCatching { sendJson(output, 500, """{"error":{"message":"internal"}}""") }
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Reads one CRLF-terminated line as bytes; the request head is ASCII. */
    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (b == '\n'.code) return buffer.toString().removeSuffix("\r")
            buffer.append(b.toChar())
        }
    }

    private fun chat(
        output: OutputStream,
        body: String,
        contentType: String?,
        auth: AuthContext = AuthContext.DEFAULT,
    ) {
        // The upstream rejects non-JSON, and allowing form posts would let a
        // simple cross-site request drive a paid model call.
        if (contentType == null || !contentType.trim().lowercase().startsWith("application/json")) {
            sendJson(output, 415, """{"error":{"message":"Content-Type must be application/json","type":"unsupported_media_type"}}""")
            return
        }
        if (runCatching { JSONObject(body) }.isFailure) {
            sendJson(output, 400, """{"error":{"message":"invalid json"}}""")
            return
        }
        val model = runCatching { JSONObject(body).optString("model") }.getOrDefault("")

        // 白名单校验放在调用上游之前：不合规的模型不应产生任何上游开销。
        if (model.isNotBlank() && !auth.modelAllowed(model)) {
            sendJson(
                output,
                403,
                """{"error":{"message":"model '$model' is not allowed for this api key","type":"model_not_allowed"}}""",
            )
            return
        }

        // Qoder 走自己的令牌体系，不依赖 WorkBuddy 的凭证。
        if (useQoder()) {
            val account = qoderAccount()
            if (account == null) {
                sendJson(output, 401, """{"error":{"message":"no qoder credential","type":"not_signed_in"}}""")
                return
            }
            val startedAt = System.currentTimeMillis()
            when (val result = QoderChat.open(account, body, qoderModels())) {
                is QoderChat.Result.Failed -> {
                    BridgeStatus.recordError("qoder: ${result.message.take(80)}")
                    onCall(
                        CallRecord(
                            timestamp = startedAt,
                            model = model,
                            outcome = CallRecord.Outcome.FAILED,
                            detail = "qoder: ${result.message.take(110)}",
                        ),
                    )
                    val status = if (result.status in 400..599) result.status else 502
                    sendJson(
                        output,
                        status,
                        """{"error":{"message":"qoder ${result.message.replace("\"", "'").take(180)}","type":"qoder_error"}}""",
                    )
                }
                is QoderChat.Result.Ok -> {
                    val usage = relay(output, result.stream) { result.stream.close() }
                    onCall(
                        CallRecord(
                            timestamp = startedAt,
                            model = model,
                            outcome = CallRecord.Outcome.OK,
                            promptTokens = usage.prompt,
                            completionTokens = usage.completion,
                            credits = usage.credits,
                        ),
                    )
                    onKeyUsage(auth.keyId, usage.credits)
                }
            }
            return
        }

        val cred = credential()
        if (cred == null) {
            sendJson(output, 401, """{"error":{"message":"no credential","type":"not_signed_in"}}""")
            return
        }

        val started = System.currentTimeMillis()
        when (val result = client.chatStream(cred, body)) {
            is UpstreamClient.ChatResult.Failed -> {
                val detail = Wire.displayError(result.message) ?: result.message.take(200)
                BridgeStatus.recordError("${result.kind.name.lowercase()}: $detail")
                // A 401 keeps its status note so the caller can classify it as
                // auth; every other failure speaks through the message alone,
                // because a bare status word would mask a business error.
                val note = if (result.status == 401) " (http 401)" else ""
                onCall(
                    CallRecord(
                        timestamp = started,
                        model = model,
                        outcome = CallRecord.Outcome.FAILED,
                        detail = "${result.kind.name.lowercase()}: $detail".take(120),
                    ),
                )
                sendJson(
                    output,
                    KIND_STATUS.getValue(result.kind),
                    """{"error":{"message":"workbuddy ${result.kind.name.lowercase()}$note: ${detail.replace("\"", "'")}","type":"${result.kind.name.lowercase()}"}}""",
                )
            }
            is UpstreamClient.ChatResult.Ok -> {
                try {
                    val usage = relay(output, result.stream) { result.connection.disconnect() }
                    onCall(
                        CallRecord(
                            timestamp = started,
                            model = model,
                            outcome = CallRecord.Outcome.OK,
                            promptTokens = usage.prompt,
                            completionTokens = usage.completion,
                            credits = usage.credits,
                        ),
                    )
                    // 只有成功调用才计入该密钥的积分消耗，
                    // 失败不扣预算，避免上游抖动把用户额度吃掉。
                    onKeyUsage(auth.keyId, usage.credits)
                } finally {
                    runCatching { result.connection.disconnect() }
                }
            }
        }
    }

    /**
     * Streams the upstream answer through unchanged.
     *
     * The stream stays open for the whole generation, so it is forwarded frame
     * by frame — buffering it would stall the caller until the answer finished.
     */
    private fun relay(
        output: OutputStream,
        stream: java.io.InputStream,
        close: () -> Unit,
    ): SseUsage {
        output.write(
            (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                ).toByteArray(StandardCharsets.US_ASCII),
        )
        output.flush()
        val buffer = ByteArray(4096)
        // The usage block arrives in a trailing SSE frame, so the stream is
        // scanned as it is forwarded: the same bytes go to the client, and the
        // last complete usage object seen is what the call gets billed for.
        val tail = StringBuilder()
        var usage = SseUsage()
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
                tail.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                if (tail.length > USAGE_SCAN_BYTES) {
                    tail.delete(0, tail.length - USAGE_SCAN_BYTES)
                }
                usage = parseUsage(tail.toString()) ?: usage
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "chat relay interrupted", e)
        } finally {
            runCatching { close() }
        }
        return usage
    }

    /** Token and credit figures reported by the upstream for one call. */
    private data class SseUsage(
        val prompt: Int = 0,
        val completion: Int = 0,
        val credits: Double = 0.0,
    )

    /**
     * Reads the newest usage block out of the accumulated stream tail.
     *
     * Scanned rather than accumulated per frame because the block is a plain
     * JSON object inside SSE data lines, and only the final one carries the
     * totals.
     */
    private fun parseUsage(text: String): SseUsage? {
        val index = text.lastIndexOf("\"usage\"")
        if (index < 0) return null
        val open = text.indexOf('{', index)
        if (open < 0) return null
        // The usage object is nested (it carries a token-details member), so the
        // end is found by brace depth rather than by the first closing brace.
        var depth = 0
        var end = -1
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end < 0) return null
        return runCatching {
            val usage = JSONObject(text.substring(open, end + 1))
            SseUsage(
                prompt = usage.optInt("prompt_tokens", 0),
                completion = usage.optInt("completion_tokens", 0),
                credits = usage.optDouble("credit", 0.0),
            )
        }.getOrNull()
    }

    private fun sendJson(output: OutputStream, code: Int, payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val head = (
            "HTTP/1.1 $code ${statusText(code)}\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            ).toByteArray(StandardCharsets.US_ASCII)
        runCatching {
            output.write(head)
            output.write(bytes)
            output.flush()
        }
    }

    /** Strips an optional port, IPv6-bracket aware. */
    private fun hostnameOf(host: String): String {
        var name = host.trim().lowercase()
        if (name.startsWith("[")) {
            val end = name.indexOf(']')
            return if (end == -1) name else name.substring(0, end + 1)
        }
        val colon = name.lastIndexOf(':')
        if (colon != -1 && !name.substring(0, colon).contains(":") &&
            name.substring(colon + 1).all { it.isDigit() }
        ) {
            name = name.substring(0, colon)
        }
        return name
    }

    /** Whether a Host header names the loopback interface. */
    private fun hostIsLoopback(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return hostnameOf(host) in loopbackHosts
    }

    /** Whether a browser-sent Origin names loopback; an absent Origin passes. */
    private fun originIsLoopback(origin: String?): Boolean {
        if (origin.isNullOrBlank()) return true
        return runCatching {
            val host = java.net.URI(origin).host?.lowercase()
            host != null && (host in loopbackHosts || host == "::1")
        }.getOrDefault(false)
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        403 -> "Forbidden"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        429 -> "Too Many Requests"
        else -> "OK"
    }

    private companion object {
        const val TAG = "LyraProxy"

        /** How much of the stream tail is kept for usage scanning. */
        const val USAGE_SCAN_BYTES = 8192

        /** HTTP status each failure class surfaces as. */
        val KIND_STATUS = mapOf(
            Wire.ErrorKind.HARD_CREDIT to 402,
            Wire.ErrorKind.SOFT_RATE to 429,
            Wire.ErrorKind.SESSION_DEAD to 401,
            Wire.ErrorKind.NOT_FOUND to 502,
            Wire.ErrorKind.SERVER to 502,
            Wire.ErrorKind.CLIENT to 400,
        )
    }
}
