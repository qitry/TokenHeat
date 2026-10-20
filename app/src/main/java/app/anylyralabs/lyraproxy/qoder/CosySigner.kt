package app.anylyralabs.lyraproxy.qoder

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * COSY 签名。
 *
 * Qoder 的 /algo 接口不认普通 Bearer，而是要求一套 `Bearer COSY.*` 令牌：
 * 请求元信息加密后放进 Authorization，再用 RSA 加密的对称密钥与 MD5 摘要做完整性校验。
 *
 * 实现依据官方 CLI 内嵌的 WASM 模块（`qoder_auth_wasm_bg.wasm`），
 * 其中的 RSA 公钥与签名格式均已实测可用：
 * 用伪造令牌请求模型列表也返回 200，说明签名本身被接受。
 */
object CosySigner {

    /** CLI 内嵌的 RSA 公钥；用于加密每次请求随机生成的 AES 密钥。 */
    private const val PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDA8iMH5c02LilrsERw9t6Pv5Nc
4k6Pz1EaDicBMpdpxKduSZu5OANqUq8er4GM95omAGIOPOh+Nx0spthYA2BqGz+l
6HRkPJ7S236FZz73In/KVuLnwI8JJ2CbuJap8kvheCCZpmAWpb/cPx/3Vr/J6I17
XcW+ML9FoCI6AOvOzwIDAQAB
-----END PUBLIC KEY-----
"""

    data class Signed(
        val headers: Map<String, String>,
    )

    /**
     * 生成一次请求的完整头集合。
     *
     * @param path 不含 `/algo` 前缀的路径，签名与 `Cosy-Sigpath` 都用它
     * @param body 请求体原文；GET 传空串
     */
    fun sign(
        method: String,
        path: String,
        body: String,
        token: String,
        uid: String,
        name: String,
        email: String,
        machineId: String,
        version: String = QoderConfig.CLI_VERSION,
    ): Signed {
        val aesKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val requestId = UUID.randomUUID().toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        val userInfo = JSONObject().apply {
            put("uid", uid)
            put("security_oauth_token", token)
            put("name", name)
            put("aid", "")
            put("email", email)
        }.toString()

        val info = base64(aesEncrypt(userInfo.toByteArray(Charsets.UTF_8), aesKey))
        val cosyKey = base64(rsaEncrypt(aesKey.toByteArray(Charsets.UTF_8)))

        val payload = JSONObject().apply {
            put("version", "v1")
            put("requestId", requestId)
            put("info", info)
            put("cosyVersion", version)
            put("ideVersion", "")
        }.toString()
        val payloadB64 = base64(payload.toByteArray(Charsets.UTF_8))

        // 签名串：payload、密钥、时间戳、请求体、路径，以换行分隔。
        val signature = md5(
            listOf(payloadB64, cosyKey, timestamp, body, path).joinToString("\n")
                .toByteArray(Charsets.UTF_8),
        )

        return Signed(
            headers = mapOf(
                "Authorization" to "Bearer COSY.$payloadB64.$signature",
                "Cosy-Key" to cosyKey,
                "Cosy-User" to uid,
                "Cosy-Date" to timestamp,
                "Cosy-Version" to version,
                "Cosy-Machineid" to machineId,
                "Cosy-Machinetoken" to machineId,
                "Cosy-Machinetype" to "5",
                "Cosy-Machineos" to machineOs(),
                "Cosy-Clienttype" to QoderConfig.CLIENT_TYPE,
                "Cosy-Clientip" to "127.0.0.1",
                "Cosy-Bodyhash" to md5(body.toByteArray(Charsets.UTF_8)),
                "Cosy-Bodylength" to body.toByteArray(Charsets.UTF_8).size.toString(),
                "Cosy-Sigpath" to path,
                "Cosy-Data-Policy" to "agree",
                "Cosy-Organization-Id" to "",
                "Cosy-Organization-Tags" to "",
                "Login-Version" to "v2",
                "X-Request-Id" to requestId,
            ),
        )
    }

    // --------------------------------------------------------------------- //

    private fun aesEncrypt(plain: ByteArray, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(keyBytes),
        )
        return cipher.doFinal(plain)
    }

    private fun rsaEncrypt(plain: ByteArray): ByteArray {
        val der = Base64.decode(
            PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), ""),
            Base64.DEFAULT,
        )
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plain)
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun machineOs(): String {
        val arch = when (System.getProperty("os.arch").orEmpty()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> System.getProperty("os.arch").orEmpty()
        }
        return "${arch}_android"
    }
}
