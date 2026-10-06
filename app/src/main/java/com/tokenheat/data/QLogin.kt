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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Qoder sign-in, mirroring the official CLI's device flow (extracted from the
 * `@qoder-ai/qodercli` bundle: `startDeviceFlow` + `deviceToken/poll`).
 *
 * Shape: build a PKCE pair, open `{web}/device/selectAccounts?...` in a
 * browser, poll `GET {openapi}/api/v1/deviceToken/poll`, then read the user
 * info. The browser step needs an explicit consent tap — a bare platform
 * login is not enough (same lesson as the ZCode flow).
 */
object QLogin {

    /** Which Qoder account system a sign-in targets. */
    enum class QRegion(val webBase: String, val openApi: String, val provider: Provider) {
        CN("https://qoder.com.cn", "https://openapi.qoder.com.cn", Provider.QODER_CN),
        GLOBAL("https://qoder.sh", "https://openapi.qoder.sh", Provider.QODER_GLOBAL),
    }

    data class Session(
        val region: QRegion,
        val authUrl: String,
        val nonce: String,
        val verifier: String,
        val machineId: String,
    )

    /** Short-lived device credential returned by a completed poll. */
    data class DeviceAuth(
        val token: String,
        val refreshToken: String,
        val expiresAt: Long,
        val uid: String = "",
        val name: String = "",
    )

    /**
     * Starts a device flow; the caller opens [Session.authUrl] in a browser.
     * `machineId` is a per-install random UUID, like the CLI's persisted one.
     */
    fun start(region: QRegion, machineId: String): Session {
        val verifier = pkceVerifier()
        val challenge = pkceChallenge(verifier)
        val nonce = UUID.randomUUID().toString()
        val params = "challenge=$challenge&challenge_method=S256" +
            "&nonce=$nonce&machine_id=$machineId&client_id=$CLIENT_ID"
        return Session(
            region = region,
            authUrl = "${region.webBase}/device/selectAccounts?$params",
            nonce = nonce,
            verifier = verifier,
            machineId = machineId,
        )
    }

    sealed class Poll {
        object Pending : Poll()
        data class Done(val auth: DeviceAuth) : Poll()
        data class Failed(val message: String) : Poll()
    }

    /**
     * Polls one flow. Unknown-but-tokenless answers stay pending (a bogus
     * nonce also 404s, and the deadline bounds the wait); anything carrying
     * an explicit error code other than `NotFound` fails fast.
     */
    fun poll(session: Session): Poll {
        val url = "${session.region.openApi}/api/v1/deviceToken/poll" +
            "?nonce=${session.nonce}&verifier=${session.verifier}&challenge_method=S256"
        val conn = open(url).apply { requestMethod = "GET" }
        val text = try {
            read(conn)
        } catch (e: Exception) {
            return Poll.Pending
        } finally {
            conn.disconnect()
        }
        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return Poll.Pending
        val data = payload.optJSONObject("data")?.takeIf { it.length() > 0 } ?: payload
        val token = data.optString("device_token").ifEmpty {
            data.optString("token").ifEmpty { data.optString("access_token") }
        }
        if (token.isNotEmpty()) {
            return Poll.Done(
                DeviceAuth(
                    token = token,
                    refreshToken = data.optString("refresh_token").ifEmpty { data.optString("refreshToken") },
                    expiresAt = normalizeExpiry(
                        data.optLong("expires_at", 0L).takeIf { it > 0 }
                            ?: data.optLong("expire_time", 0L).takeIf { it > 0 }
                            ?: data.optLong("expiresAt", 0L).takeIf { it > 0 }
                            ?: data.optLong("expires_in", 0L).takeIf { it > 0 }
                            ?: data.optLong("expiresIn", 0L),
                        duration = data.has("expires_in") || data.has("expiresIn"),
                    ),
                    uid = data.optString("uid").ifEmpty { data.optString("user_id") },
                    name = data.optString("name").ifEmpty { data.optString("nickname") },
                ),
            )
        }
        val errorCode = payload.optString("errorCode").ifEmpty { payload.optString("code") }
        if (errorCode.isNotEmpty() && errorCode != "NotFound") {
            return Poll.Failed(payload.optString("errorMessage").ifEmpty { payload.optString("msg", errorCode) })
        }
        return Poll.Pending
    }

    /** Fills uid/nickname for a fresh device token; best-effort. */
    fun userinfo(region: QRegion, deviceToken: String): Pair<String, String> {
        val text = runCatching {
            get("${region.openApi}/api/v1/userinfo", mapOf("Authorization" to "Bearer $deviceToken"))
        }.getOrNull() ?: return "" to ""
        val data = runCatching { JSONObject(text).optJSONObject("data") ?: JSONObject(text) }.getOrNull()
            ?: return "" to ""
        val uid = data.optString("uid").ifEmpty { data.optString("user_id") }
        val name = data.optString("name").ifEmpty { data.optString("nickname") }
        return uid to name
    }

    fun toCredential(region: QRegion, auth: DeviceAuth, uid: String, name: String): Credential = Credential(
        provider = region.provider,
        accessToken = auth.token,
        refreshToken = auth.refreshToken,
        expiresAt = auth.expiresAt,
        domain = region.openApi,
        uid = uid.ifEmpty { auth.uid },
        nickname = name.ifEmpty { auth.name },
        source = "oauth-qoder",
    )

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private fun pkceVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
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

    /**
     * Normalizes absolute instants (s or ms) and durations into epoch seconds.
     * Mirrors the `expiresAt < 1e12 → seconds` convention used elsewhere.
     */
    fun normalizeExpiry(value: Long, duration: Boolean = false): Long {
        if (value <= 0) return 0L
        if (duration) return System.currentTimeMillis() / 1000 + value
        return if (value < 1_000_000_000_000L) value else value / 1000
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

    /**
     * OAuth client id from the official CLI bundle (`Nac`, prod). The
     * alternate `Lac` (`e93fe488-5778-4c35-a6fc-0f54ed7b3139`) is kept
     * here in case a build rejects this one with `invalid_client`.
     */
    const val CLIENT_ID = "e883ade2-e6e3-4d6d-adf7-f92ceff5fdcb"
}


}
