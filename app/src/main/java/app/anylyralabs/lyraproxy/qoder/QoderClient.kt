package app.anylyralabs.lyraproxy.qoder

import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Qoder 国际版客户端。
 *
 * 端点依据 2026-10 的实际探测与官方 CLI 反编译：
 * 设备码与账号接口在 `openapi.qoder.sh`，模型与聊天在 `api3.qoder.sh`。
 * `api.qoder.com` 是 Cloud Agents API，不用于 CLI 登录，误用会得到 401。
 */
object QoderConfig {
    /**
     * 认证与账号接口（设备码轮询、刷新、用户信息）。
     *
     * 注意：`api.qoder.com` 是 Cloud Agents API，不是 CLI 网关；
     * 对设备码轮询会返回 401。CLI 的认证域是 `openapi.qoder.sh`。
     */
    const val OPENAPI_URL = "https://openapi.qoder.sh"

    /** 模型与聊天网关（路径以 /algo 开头）。 */
    const val GATEWAY_URL = "https://api3.qoder.sh"

    /** 浏览器授权页。 */
    const val DEVICE_PAGE = "https://qoder.com/device/selectAccounts"

    /** 官方 CLI 使用的 OAuth 客户端 ID。 */
    const val CLIENT_ID = "e883ade2-e6e3-4d6d-adf7-f92ceff5fdcb"

    /** 与官方 CLI 对齐的版本号，部分接口会按它做兼容分支。 */
    const val CLI_VERSION = "1.1.62"

    const val CLIENT_TYPE = "5"

    /** 设备码有效期 5 分钟，轮询间隔 1 秒。 */
    const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
    const val POLL_INTERVAL_MS = 1000L
}

class QoderClient {

    // --------------------------------------------------------------------- //
    // 设备码登录
    // --------------------------------------------------------------------- //

    fun startDeviceFlow(machineId: String): QoderDeviceFlow {
        val verifier = randomVerifier()
        val challenge = challengeOf(verifier)
        val nonce = UUID.randomUUID().toString()
        val url = buildString {
            append(QoderConfig.DEVICE_PAGE)
            append("?challenge=").append(challenge)
            append("&challenge_method=S256")
            append("&nonce=").append(nonce)
            append("&machine_id=").append(machineId)
            append("&client_id=").append(QoderConfig.CLIENT_ID)
        }
        return QoderDeviceFlow(authUrl = url, nonce = nonce, verifier = verifier, machineId = machineId)
    }

    /**
     * 轮询一次。
     *
     * 404 表示「还没完成授权」，是官方 CLI 约定的等待状态；
     * 其它非 2xx 都视为失败，并把响应体带出去便于排查。
     */
    fun poll(flow: QoderDeviceFlow): QoderPoll {
        val url = "${QoderConfig.OPENAPI_URL}/api/v1/deviceToken/poll" +
            "?nonce=${enc(flow.nonce)}&verifier=${enc(flow.verifier)}&challenge_method=S256"
        val response = call(url, "GET", emptyMap())
        if (response.status == 404) return QoderPoll.Pending
        if (response.status !in 200..299) {
            return QoderPoll.Failed("轮询失败：HTTP ${response.status} ${response.body.take(160)}")
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return QoderPoll.Failed("授权响应不是 JSON")
        val token = json.optString("token")
        if (token.isEmpty()) return QoderPoll.Pending
        val account = accountFrom(
            json = json,
            token = token,
            machineId = flow.machineId,
            fallbackName = "",
            fallbackEmail = "",
            source = "oauth",
        )
        return QoderPoll.Done(enrich(account))
    }

    // --------------------------------------------------------------------- //
    // PAT 登录
    // --------------------------------------------------------------------- //

    /**
     * 用个人访问令牌登录。
     *
     * 先尝试直接把 PAT 当 Bearer 用；若被拒，再按官方 CLI 的方式
     * 走一次 jobToken 交换。两条路径都失败才报错，这样无论服务端
     * 采用哪种语义都能工作。
     */
    fun loginWithPat(pat: String, machineId: String): Result<QoderAccount> {
        val trimmed = pat.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalStateException("令牌为空"))

        // PAT（pt-）不是 Bearer：必须先换成 job token（jt-），官方从不直接使用 pt-。
        // 路径二：PAT 换取 job token
        val exchange = call(
            "${QoderConfig.OPENAPI_URL}/api/v1/jobToken/exchange",
            "POST",
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            JSONObject().put("personal_token", trimmed).toString(),
        )
        if (exchange.status !in 200..299) {
            return Result.failure(
                IllegalStateException("令牌无效：HTTP ${exchange.status} ${exchange.body.take(160)}"),
            )
        }
        val json = runCatching { JSONObject(exchange.body) }.getOrNull()
            ?: return Result.failure(IllegalStateException("令牌交换响应不是 JSON"))
        val token = json.optString("token")
            .ifEmpty { json.optString("access_token") }
            .ifEmpty { json.optString("device_token") }
        if (token.isEmpty()) return Result.failure(IllegalStateException("令牌交换未返回 token"))
        val account = accountFrom(json, token, machineId, "", "", "pat")
        return Result.success(enrich(account))
    }

    // --------------------------------------------------------------------- //
    // 刷新与用户信息
    // --------------------------------------------------------------------- //

    /**
     * 刷新访问令牌；失败时返回原账号，让上层决定是否提示重新登录。
     *
     * 两种 token 走不同接口：
     * - 设备码登录：POST /api/v1/deviceToken/refresh，响应字段是 device_token；
     * - PAT / job token：POST /api/v1/jobToken/refresh，响应字段是 token。
     */
    fun refresh(account: QoderAccount): QoderAccount {
        if (account.refreshToken.isBlank()) return account

        val jobToken = account.source == "pat" || account.accessToken.startsWith("jt-")
        val path = if (jobToken) "/api/v1/jobToken/refresh" else "/api/v1/deviceToken/refresh"
        val payload = JSONObject().put("refresh_token", account.refreshToken)

        val response = call(
            "${QoderConfig.OPENAPI_URL}$path",
            "POST",
            mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
            payload.toString(),
        )
        if (response.status !in 200..299) return account

        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return account
        val token = json.optString("token")
            .ifEmpty { json.optString("device_token") }
            .ifEmpty { json.optString("access_token") }
        if (token.isEmpty()) return account

        val refreshed = accountFrom(
            json = json,
            token = token,
            machineId = account.machineId,
            fallbackName = account.name,
            fallbackEmail = account.email,
            source = account.source,
            fallbackId = account.id,
        )
        return refreshed.copy(
            id = refreshed.id.ifEmpty { account.id },
            name = refreshed.name.ifBlank { account.name },
            email = refreshed.email.ifBlank { account.email },
            refreshToken = refreshed.refreshToken.ifBlank { account.refreshToken },
        )
    }

    private data class UserInfo(val id: String, val name: String, val email: String)

    private fun fetchUserInfo(token: String): UserInfo? {
        val response = call(
            "${QoderConfig.OPENAPI_URL}/api/v1/userinfo",
            "GET",
            mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $token",
            ),
        )
        if (response.status !in 200..299) return null
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        return UserInfo(
            id = json.optString("id").ifEmpty { json.optString("uid") },
            name = json.optString("name").ifEmpty { json.optString("username") },
            email = json.optString("email"),
        )
    }

    /** 补全昵称与邮箱；失败不影响登录本身。 */
    private fun enrich(account: QoderAccount): QoderAccount {
        val info = fetchUserInfo(account.accessToken) ?: return account
        return account.copy(
            name = info.name.ifBlank { account.name },
            email = info.email.ifBlank { account.email },
        )
    }

    // --------------------------------------------------------------------- //
    // 模型
    // --------------------------------------------------------------------- //

    fun fetchModels(account: QoderAccount): List<QoderModel> {
        val signed = CosySigner.sign(
            method = "GET",
            path = "/api/v2/model/list",
            body = ByteArray(0),
            token = account.accessToken,
            uid = account.id,
            name = account.name,
            email = account.email,
            machineId = account.machineId,
        )
        val response = call(
            "${QoderConfig.GATEWAY_URL}/algo/api/v2/model/list?Encode=1",
            "GET",
            signed.headers + ("Accept" to "application/json"),
        )
        if (response.status !in 200..299) {
            throw IllegalStateException("模型列表失败：HTTP ${response.status} ${response.body.take(200)}")
        }
        val root = runCatching { JSONObject(response.body) }.getOrElse {
            throw IllegalStateException("模型列表不是 JSON")
        }
        val chat = root.optJSONArray("chat") ?: return emptyList()
        val result = ArrayList<QoderModel>(chat.length())
        for (index in 0 until chat.length()) {
            val item = chat.optJSONObject(index) ?: continue
            if (!item.optBoolean("enable", true)) continue
            val key = item.optString("key")
            val display = item.optString("display_name")
            if (key.isEmpty() && display.isEmpty()) continue
            result.add(
                QoderModel(
                    key = key,
                    displayName = display.ifEmpty { key },
                    supportsImages = item.optBoolean("is_vl", false),
                    supportsReasoning = item.optBoolean("is_reasoning", false) ||
                        item.has("thinking_config"),
                    contextWindow = maxContext(item.optJSONObject("context_config")),
                    priceFactor = item.optDouble("price_factor", 0.0),
                    raw = item.toString(),
                ),
            )
        }
        return result
    }

    private fun maxContext(config: JSONObject?): Int {
        if (config == null) return 0
        var max = 0
        val keys = config.keys()
        while (keys.hasNext()) {
            val entry = config.optJSONObject(keys.next()) ?: continue
            val count = entry.optInt("token_count", 0)
            if (count > max) max = count
        }
        return max
    }

    // --------------------------------------------------------------------- //
    // 连通性测试
    // --------------------------------------------------------------------- //

    // --------------------------------------------------------------------- //
    // 额度
    // --------------------------------------------------------------------- //

    /**
     * 读取额度。
     *
     * `/api/v2/quota/usage` 走 openapi 的 Bearer，不需要 COSY 签名。
     * 三类额度都可能缺席，因此逐项解析而不是假定结构完整。
     */
    fun fetchUsage(
        account: QoderAccount,
        onRefreshed: ((QoderAccount) -> Unit)? = null,
    ): QoderUsage {
        var current = account
        var response = call(
            "${QoderConfig.OPENAPI_URL}/api/v2/quota/usage",
            "GET",
            mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer ${current.accessToken}",
            ),
        )
        // job token 只有约 24 小时：401 时先刷新再重试一次，避免额度面板长期不可用。
        if (response.status == 401) {
            val refreshed = refresh(current)
            if (refreshed.accessToken != current.accessToken) {
                current = refreshed
                onRefreshed?.invoke(refreshed)
                response = call(
                    "${QoderConfig.OPENAPI_URL}/api/v2/quota/usage",
                    "GET",
                    mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer ${current.accessToken}",
                    ),
                )
            }
        }
        if (response.status !in 200..299) {
            throw IllegalStateException("额度查询失败：HTTP ${response.status} ${response.body.take(160)}")
        }
        val root = runCatching { JSONObject(response.body) }.getOrElse {
            throw IllegalStateException("额度响应不是 JSON")
        }
        return QoderUsage(
            userQuota = bucket(root.optJSONObject("userQuota"), "total"),
            addOnQuota = bucket(root.optJSONObject("addOnQuota"), "total"),
            orgQuota = bucket(root.optJSONObject("orgResourcePackage"), "cap"),
            expiresAt = parseExpiry(root),
        )
    }

    private fun bucket(json: JSONObject?, totalKey: String): QoderQuota? {
        if (json == null) return null
        val total = numberOrNull(json, totalKey) ?: numberOrNull(json, "total")
        val used = numberOrNull(json, "used")
        val remaining = numberOrNull(json, "remaining")
        val unit = json.optString("unit")
        if (total == null && used == null && remaining == null) return null
        return QoderQuota(total = total, used = used, remaining = remaining, unit = unit)
    }

    private fun numberOrNull(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    // --------------------------------------------------------------------- //
    // 内部
    // --------------------------------------------------------------------- //

    private data class Response(val status: Int, val body: String)

    private fun call(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
        readTimeoutMs: Int = 30_000,
    ): Response {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val stream: InputStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
            Response(status, text)
        } catch (error: Exception) {
            Response(0, error.message ?: "transport error")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun machineOs(): String {
        val arch = when (System.getProperty("os.arch").orEmpty()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> System.getProperty("os.arch").orEmpty()
        }
        return "${arch}_android"
    }

    private fun accountFrom(
        json: JSONObject,
        token: String,
        machineId: String,
        fallbackName: String,
        fallbackEmail: String,
        source: String,
        fallbackId: String = "qoder",
    ): QoderAccount {
        val expiresAt = parseExpiry(json)
        val uid = json.optString("user_id").ifEmpty { json.optString("uid") }
        return QoderAccount(
            id = uid.ifEmpty { fallbackId },
            name = json.optString("name").ifEmpty { fallbackName },
            email = json.optString("email").ifEmpty { fallbackEmail },
            accessToken = token,
            refreshToken = json.optString("refresh_token"),
            expiresAt = expiresAt,
            machineId = machineId,
            source = source,
        )
    }

    /**
     * Qoder 的过期字段在不同响应里有三种写法：ISO 时间串、epoch 秒、
     * 以及 expires_in 时长。统一归一成毫秒时间戳。
     */
    private fun parseExpiry(json: JSONObject): Long {
        val absolute = json.optString("expires_at").ifBlank { json.optString("expiresAt") }
        if (absolute.isNotBlank()) {
            runCatching { java.time.Instant.parse(absolute).toEpochMilli() }
                .getOrNull()?.let { return it }
            absolute.toLongOrNull()?.let { value ->
                return if (value > 100_000_000_000L) value else value * 1000L
            }
        }
        val expiresIn = json.optLong("expires_in", 0L)
        if (expiresIn > 0) {
            return System.currentTimeMillis() +
                (if (expiresIn > 100_000L) expiresIn else expiresIn * 1000L)
        }
        return 0L
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
