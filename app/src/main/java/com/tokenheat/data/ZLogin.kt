package com.tokenheat.data

import com.tokenheat.proto.Credential
import com.tokenheat.proto.Provider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * ZCode (Z.AI) sign-in, mirroring the official CLI's browser flow.
 *
 * Shape: ask `zcode.z.ai` for an auth URL, let the user complete it in a
 * browser, poll until the OAuth token lands, then exchange it — business
 * login, default org/project lookup, ensure an API key named
 * `zcode-api-key`, decrypt its secret — into the `{apiKey}.{secretKey}`
 * credential that `api.z.ai` accepts.
 *
 * The JWT/Coding-Plan route (`zcode.z.ai/.../anthropic`) is deliberately
 * NOT used: it requires an Alibaba traceless-verification header whose
 * solver needs Node + jsdom, which is not available on-device. The API-key
 * route needs no captcha and speaks plain OpenAI dialect.
 */
object ZLogin {

    private const val OAUTH_BASE = "https://zcode.z.ai/api/v1"
    private const val BIZ_BASE = "https://api.z.ai"
    private const val KEY_NAME = "zcode-api-key"

    data class Session(val flowId: String, val authUrl: String, val pollToken: String)

    /** Starts an OAuth flow; the caller opens [Session.authUrl] in a browser. */
    fun start(): Session {
        val pollToken = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val conn = open("$OAUTH_BASE/oauth/cli/init").apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $pollToken")
            doOutput = true
        }
        val text = try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(JSONObject().put("provider", "zai").toString())
            }
            read(conn)
        } finally {
            conn.disconnect()
        }
        val data = runCatching { JSONObject(text).optJSONObject("data") }.getOrNull()
            ?: throw RuntimeException("OAuth 初始化返回异常")
        val flowId = data.optString("flow_id")
        val authUrl = data.optString("authorize_url")
        if (flowId.isEmpty() || authUrl.isEmpty()) throw RuntimeException("缺少 flow_id/authorize_url")
        return Session(flowId, authUrl, pollToken)
    }

    sealed class Poll {
        object Pending : Poll()
        data class Done(val oauthToken: String) : Poll()
        data class Failed(val message: String) : Poll()
    }

    /**
     * Polls one flow. The upstream envelope carries `code != 0` on hard
     * failure; a zero code without any token field means the user has not
     * finished in the browser yet, so every known token spelling is accepted.
     */
    fun poll(session: Session): Poll {
        val conn = open("$OAUTH_BASE/oauth/cli/poll/${session.flowId}").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${session.pollToken}")
        }
        val text = try {
            read(conn)
        } catch (e: Exception) {
            return Poll.Pending
        } finally {
            conn.disconnect()
        }
        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return Poll.Pending
        val code = payload.optInt("code", 0)
        val data = payload.optJSONObject("data")
        // Prefer an explicit token wherever the gateway puts it.
        val token = data?.let {
            it.optString("access_token").ifEmpty {
                it.optString("accessToken").ifEmpty { it.optString("token") }
            }
        }.orEmpty()
        if (token.isNotEmpty()) return Poll.Done(token)
        // A token can also sit at the top level of some gateway answers.
        val topToken = payload.optString("access_token").ifEmpty {
            payload.optString("accessToken").ifEmpty { payload.optString("token") }
        }
        if (topToken.isNotEmpty()) return Poll.Done(topToken)
        if (code != 0) return Poll.Failed(payload.optString("msg", "code=$code"))
        return Poll.Pending
    }

    /**
     * Exchanges an OAuth token for a stored credential. Throws with a
     * human-readable message when any step is refused.
     */
    fun exchange(oauthToken: String): Credential {
        val bizToken = bizLogin(oauthToken)
        val customer = customerInfo(bizToken)
        val (orgId, projectId) = defaultOrgProject(customer)
        val nickname = customer.optString("nickname").ifEmpty {
            customer.optString("username").ifEmpty {
                customer.optString("name").ifEmpty { "ZCode" }
            }
        }
        val apiKey = ensureApiKey(bizToken, orgId, projectId)
        val secret = copySecret(bizToken, orgId, projectId, apiKey)
        return Credential(
            provider = Provider.ZCODE,
            accessToken = "",
            apiKey = "$apiKey.$secret",
            expiresAt = 0L,
            domain = BIZ_BASE,
            uid = customer.optString("id").ifEmpty { customer.optString("userId") },
            nickname = nickname,
            source = "oauth-zcode",
        )
    }

    /** OAuth token -> business token. */
    private fun bizLogin(oauthToken: String): String {
        val text = postJson(
            "$BIZ_BASE/api/auth/z/login",
            mapOf("Content-Type" to "application/json"),
            JSONObject().put("token", oauthToken).toString(),
        )
        val data = runCatching { JSONObject(text).optJSONObject("data") }.getOrNull()
            ?: throw RuntimeException("业务登录返回异常")
        return data.optString("access_token").ifEmpty { data.optString("accessToken") }
            .ifEmpty { throw RuntimeException("业务登录未返回凭证") }
    }

    private fun customerInfo(bizToken: String): JSONObject {
        val text = get(
            "$BIZ_BASE/api/biz/customer/getCustomerInfo",
            mapOf("Authorization" to "Bearer $bizToken"),
        )
        return runCatching { JSONObject(text).optJSONObject("data") }
            .getOrNull() ?: throw RuntimeException("获取用户信息失败")
    }

    /** Picks the default org/project, falling back to the first of each. */
    private fun defaultOrgProject(customer: JSONObject): Pair<String, String> {
        val orgs = customer.optJSONArray("organizations") ?: throw RuntimeException("找不到可用机构")
        var org = (0 until orgs.length()).map { orgs.optJSONObject(it) }.firstOrNull()
            ?: throw RuntimeException("找不到可用机构")
        org = (0 until orgs.length()).map { orgs.optJSONObject(it) }
            .firstOrNull { (it.optString("organizationName")).contains("默认机构") } ?: org
        val projects = org.optJSONArray("projects") ?: throw RuntimeException("找不到可用项目")
        var proj = (0 until projects.length()).map { projects.optJSONObject(it) }.firstOrNull()
            ?: throw RuntimeException("找不到可用项目")
        proj = (0 until projects.length()).map { projects.optJSONObject(it) }
            .firstOrNull { it.optString("projectName").contains("默认项目") } ?: proj
        val orgId = org.optString("organizationId")
        val projectId = proj.optString("projectId")
        if (orgId.isEmpty() || projectId.isEmpty()) throw RuntimeException("机构/项目缺少 ID")
        return orgId to projectId
    }

    /** Reuses the existing key when one was already created for this app. */
    private fun ensureApiKey(bizToken: String, orgId: String, projectId: String): String {
        val base = "$BIZ_BASE/api/biz/v1/organization/$orgId/projects/$projectId/api_keys"
        val headers = mapOf("Authorization" to "Bearer $bizToken")
        val list = runCatching { JSONObject(get(base, headers)).optJSONArray("data") }.getOrNull()
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                if (item.optString("name") == KEY_NAME && item.optString("apiKey").isNotEmpty()) {
                    return item.optString("apiKey")
                }
            }
        }
        val created = runCatching {
            JSONObject(postJson(base, headers + ("Content-Type" to "application/json"), JSONObject().put("name", KEY_NAME).toString()))
        }.getOrNull()?.optJSONObject("data")
        return created?.optString("apiKey")?.ifEmpty { null }
            ?: throw RuntimeException("创建 API Key 失败")
    }

    /** Decrypts the secret half of an API key; both halves form the credential. */
    private fun copySecret(bizToken: String, orgId: String, projectId: String, apiKey: String): String {
        val text = get(
            "$BIZ_BASE/api/biz/v1/organization/$orgId/projects/$projectId/api_keys/copy/$apiKey",
            mapOf("Authorization" to "Bearer $bizToken"),
        )
        return runCatching { JSONObject(text).optJSONObject("data")?.optString("secretKey") }
            .getOrNull()?.ifEmpty { null } ?: throw RuntimeException("读取 Secret Key 失败")
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
}
