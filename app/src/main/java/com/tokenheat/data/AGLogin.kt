package com.tokenheat.data

import com.tokenheat.proto.Credential
import com.tokenheat.proto.Provider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Antigravity (Google) sign-in, mirroring the community OAuth plugin's flow
 * (constants extracted from `opencode-antigravity-auth`: Google authorize
 * URL + PKCE, `localhost:51121` callback, `oauth2.googleapis.com` exchange,
 * project via `loadCodeAssist`).
 *
 * Shape: open the authorize URL in a browser, catch the loopback callback in
 * this process, exchange the code, read the email, resolve the Cloud project.
 * Non-official use carries ban/fragility risk — the UI says so.
 */
object AGLogin {

    data class AuthRequest(val authUrl: String, val verifier: String)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val email: String,
    )

    /** Builds the browser URL; the verifier stays local for the exchange. */
    fun authRequest(): AuthRequest {
        val verifier = pkceVerifier()
        val state = android.util.Base64.encodeToString(
            JSONObject().put("verifier", verifier).put("projectId", "").toString()
                .toByteArray(StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
        val params = "client_id=$CLIENT_ID&response_type=code" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            "&scope=${urlEncode(SCOPES.joinToString(" "))}" +
            "&code_challenge=${pkceChallenge(verifier)}&code_challenge_method=S256" +
            "&state=$state&access_type=offline&prompt=consent"
        return AuthRequest(
            authUrl = "https://accounts.google.com/o/oauth2/v2/auth?$params",
            verifier = verifier,
        )
    }

    /** Loopback callback catcher: `GET /oauth-callback?code=..&state=..`. */
    class CallbackServer {
        private val socket: ServerSocket
        val port: Int get() = socket.localPort
        private val codes = ArrayBlockingQueue<Pair<String, String>>(1)
        @Volatile private var closed = false

        init {
            var last: Exception? = null
            var bound: ServerSocket? = null
            for (port in 51121..51131) {
                bound = runCatching {
                    ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
                }.getOrNull()
                if (bound != null) break
            }
            socket = bound ?: throw (last ?: IllegalStateException("cannot bind loopback"))
            Thread({
                while (!closed) {
                    val accepted = try {
                        socket.accept()
                    } catch (e: Exception) {
                        break
                    }
                    try {
                        handle(accepted)
                    } finally {
                        runCatching { accepted.close() }
                    }
                }
            }, "ag-oauth-callback").apply { isDaemon = true; start() }
        }

        private fun handle(socket: Socket) {
            socket.soTimeout = 15_000
            val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val requestLine = runCatching { input.readLine() }.getOrNull() ?: return
            var contentLength = 0
            while (true) {
                val line = runCatching { input.readLine() }?.getOrNull() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength > 0) {
                // Drain a possible body so the connection stays parseable.
                runCatching {
                    val buf = CharArray(contentLength.coerceAtMost(4096))
                    var left = contentLength
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size, left))
                        if (n < 0) break
                        left -= n
                    }
                }
            }
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val query = path.substringAfter('?', "")
            val params = query.split('&').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) urlDecode(kv[0]) to urlDecode(kv[1]) else null
            }.toMap()
            val code = params["code"].orEmpty()
            val state = params["state"].orEmpty()
            val ok = code.isNotEmpty()
            if (ok) codes.offer(code to state)
            val page = if (ok) "登录成功，请返回 App" else "登录失败或被拒绝，请返回 App 重试"
            val body = "<html><body><h3>$page</h3></body></html>"
                .toByteArray(StandardCharsets.UTF_8)
            val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            runCatching {
                socket.getOutputStream().run {
                    write(head.toByteArray(StandardCharsets.US_ASCII))
                    write(body)
                    flush()
                }
            }
        }

        /** Blocks up to [timeoutMs] for the callback; null on timeout. */
        fun awaitCode(timeoutMs: Long): Pair<String, String>? =
            runCatching { codes.poll(timeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()

        fun close() {
            closed = true
            runCatching { socket.close() }
        }
    }

    /**
     * Exchanges a code. Installed apps authenticate with PKCE alone (no
     * client secret — it cannot stay secret in a client and GitHub push
     * protection rejects it anyway). A missing refresh token is a hard
     * failure: without it the account cannot renew itself.
     */
    fun exchange(code: String, verifier: String): Tokens {
        val body = "client_id=${urlEncode(CLIENT_ID)}" +
            "&code=${urlEncode(code)}&grant_type=authorization_code" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            "&code_verifier=${urlEncode(verifier)}"
        val text = postForm("https://oauth2.googleapis.com/token", body)
        val payload = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalStateException("token 交换返回异常")
        if (payload.has("error")) {
            throw IllegalStateException("token 交换被拒绝：${payload.optString("error_description", payload.optString("error"))}")
        }
        val refresh = payload.optString("refresh_token")
        if (refresh.isEmpty()) throw IllegalStateException("未返回 refresh token（请重新确认授权）")
        val email = runCatching {
            val info = get(
                "https://www.googleapis.com/oauth2/v1/userinfo?alt=json",
                mapOf("Authorization" to "Bearer ${payload.optString("access_token")}"),
            )
            JSONObject(info).optString("email")
        }.getOrNull().orEmpty()
        return Tokens(
            accessToken = payload.optString("access_token"),
            refreshToken = refresh,
            expiresAt = System.currentTimeMillis() / 1000 + payload.optLong("expires_in", 3600L),
            email = email,
        ).also {
            if (it.accessToken.isEmpty()) throw IllegalStateException("token 交换未返回 access token")
        }
    }

    /**
     * Resolves the Cloud project for one access token, falling back to the
     * community-known default (business accounts often return none).
     */
    fun fetchProjectId(accessToken: String): String {
        for (base in ENDPOINTS_LOAD) {
            val found = runCatching {
                val text = postJson(
                    "$base/v1internal:loadCodeAssist",
                    agHeaders(accessToken),
                    JSONObject().put("metadata", JSONObject().put("ideType", "ANTIGRAVITY").put("platform", "MACOS").put("pluginType", "GEMINI")).toString(),
                )
                JSONObject(text).optString("cloudaicompanionProject").ifEmpty { null }
            }.getOrNull()
            if (!found.isNullOrEmpty()) return found
        }
        return DEFAULT_PROJECT_ID
    }

    fun toCredential(tokens: Tokens, projectId: String): Credential = Credential(
        provider = Provider.ANTIGRAVITY,
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        projectId = projectId,
        expiresAt = tokens.expiresAt,
        uid = tokens.email,
        nickname = tokens.email.substringBefore('@').ifEmpty { "Antigravity" },
        source = "oauth-antigravity",
    )

    // ------------------------------------------------------------------ //
    // Shared request pieces (mirrors the plugin's header styles).
    // ------------------------------------------------------------------ //

    fun agHeaders(accessToken: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $accessToken",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to AG_USER_AGENT,
        "X-Goog-Api-Client" to "google-cloud-sdk vscode_cloudshelleditor/0.1",
        "Client-Metadata" to """{"ideType":"ANTIGRAVITY","platform":"MACOS","pluginType":"GEMINI"}""",
    )

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private fun pkceVerifier(): String {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    private fun urlEncode(value: String): String = try {
        java.net.URLEncoder.encode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun urlDecode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    // ------------------------------------------------------------------ //
    // Transport
    // ------------------------------------------------------------------ //

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "TokenHeat/1.0")
        }

    private fun read(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val conn = open(url).apply {
            requestMethod = "GET"
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun postForm(url: String, body: String): String {
        val conn = open(url).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            doOutput = true
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, headers: Map<String, String>, body: String): String {
        val conn = open(url).apply {
            requestMethod = "POST"
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Google OAuth client extracted from the community plugin. */
        const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs",
        )
        const val REDIRECT_URI = "http://localhost:51121/oauth-callback"
        const val DEFAULT_PROJECT_ID = "rising-fact-p41fc"
        const val AG_VERSION = "1.18.3"
        const val AG_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Antigravity/1.18.3 Chrome/138.0.7204.235 Electron/37.3.1 Safari/537.36"

        /** Chat + project endpoints, daily first like the community proxies. */
        val ENDPOINTS = listOf(
            "https://daily-cloudcode-pa.sandbox.googleapis.com",
            "https://autopush-cloudcode-pa.sandbox.googleapis.com",
            "https://cloudcode-pa.googleapis.com",
        )

        /** Project discovery prefers prod. */
        val ENDPOINTS_LOAD = listOf(
            "https://cloudcode-pa.googleapis.com",
            "https://daily-cloudcode-pa.sandbox.googleapis.com",
            "https://autopush-cloudcode-pa.sandbox.googleapis.com",
        )
    }
}
