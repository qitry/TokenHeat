package app.anylyralabs.lyraproxy.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import app.anylyralabs.lyraproxy.proto.Credential
import app.anylyralabs.lyraproxy.proto.CredentialStore
import app.anylyralabs.lyraproxy.proto.SavedAccount
import app.anylyralabs.lyraproxy.proto.Wire
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 账号包的导出与导入。
 *
 * 两种入口共用同一个「账号 JSON 结构」：
 * 1. 文件：`.json.gz` 压缩包，用于跨设备迁移与备份；
 * 2. 凭证字符串：把单个账号（或一段 JSON）粘贴进来直接导入，
 *    便于用户从「凭证详情」复制后换机恢复。
 *
 * 导入一律走 [CredentialStore.save]，因此重复导入只会刷新同一个账号，
 * 不会产生重复条目。
 */
object AccountBackup {

    const val MIME_TYPE = "application/gzip"
    const val FILE_SUFFIX = ".json.gz"

    data class ImportResult(
        val success: Boolean,
        val imported: Int = 0,
        val breakdown: Map<String, Int> = emptyMap(),
        val message: String = "",
    )

    // ------------------------------------------------------------------ //
    // 导出
    // ------------------------------------------------------------------ //

    /** 把全部账号序列化为可读的 JSON 文本（不压缩）。 */
    fun exportToJsonString(store: CredentialStore): String {
        val root = JSONObject().apply {
            put("version", 1)
            put("appName", "LyraProxy")
            put("exportedAt", System.currentTimeMillis())
            put("workbuddy_cn", serialize(store.accounts(Wire.Region.CN)))
            put("workbuddy_global", serialize(store.accounts(Wire.Region.GLOBAL)))
        }
        return root.toString(2)
    }

    private fun serialize(accounts: List<SavedAccount>): JSONArray {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("nickname", account.nickname)
                    put("uid", account.uid)
                    put("domain", account.domain)
                    put("accessToken", account.accessToken)
                    put("refreshToken", account.refreshToken)
                    put("expiresAt", account.expiresAt)
                    put("enterpriseId", account.enterpriseId ?: "")
                    put("source", account.source)
                },
            )
        }
        return array
    }

    /** 生成压缩包并写入应用外部目录，返回文件与其分享 Intent。 */
    fun exportToFileAndGetIntent(context: Context, store: CredentialStore): Pair<File, Intent> {
        val jsonBytes = exportToJsonString(store).toByteArray(StandardCharsets.UTF_8)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "lyraproxy_accounts_$timestamp$FILE_SUFFIX"

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        targetDir.mkdirs()
        val file = File(targetDir, fileName)
        java.io.ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(jsonBytes) }
            file.writeBytes(baos.toByteArray())
        }

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LyraProxy 账号备份 ($timestamp)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Pair(file, shareIntent)
    }

    // ------------------------------------------------------------------ //
    // 导入
    // ------------------------------------------------------------------ //

    /** 从 `.json.gz` 输入流导入（文件导入）。 */
    fun importFromGzipStream(inputStream: InputStream, store: CredentialStore): ImportResult = runCatching {
        val text = GZIPInputStream(inputStream)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
        importFromJsonText(text, store)
    }.getOrElse {
        ImportResult(success = false, message = "文件解析失败：${it.message ?: "格式不正确"}")
    }

    /**
     * 从一段文本导入，兼容三种形态：
     * 1. 完整的备份包（含 `workbuddy_cn` / `workbuddy_global` 字段）；
     * 2. 账号数组 `[{...}]`；
     * 3. 单个凭证对象 `{...}`，即「凭证详情」里可直接复制的内容。
     */
    fun importFromJsonText(text: String, store: CredentialStore): ImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ImportResult(success = false, message = "内容为空")
        }
        return runCatching {
            val breakdown = mutableMapOf<String, Int>()
            var total = 0

            if (trimmed.startsWith("[")) {
                // 账号数组：区域由每个账号自身的 domain 推断。
                val array = JSONArray(trimmed)
                total += importArray(array, null, store, breakdown)
            } else {
                val root = JSONObject(trimmed)
                val hasBundleKeys = root.has("workbuddy_cn") || root.has("workbuddy_global")
                if (hasBundleKeys) {
                    total += importArray(root.optJSONArray("workbuddy_cn"), Wire.Region.CN, store, breakdown)
                    total += importArray(root.optJSONArray("workbuddy_global"), Wire.Region.GLOBAL, store, breakdown)
                    // 兼容旧版 TokenHeat 的键名，便于从旧备份恢复。
                    total += importArray(root.optJSONArray("workbuddyCN"), Wire.Region.CN, store, breakdown)
                    total += importArray(root.optJSONArray("workbuddyGlobal"), Wire.Region.GLOBAL, store, breakdown)
                } else {
                    // 单个凭证对象
                    val account = readAccount(root, null) ?: return ImportResult(
                        success = false,
                        message = "缺少 accessToken，无法识别为账号凭证",
                    )
                    val region = account.region
                    store.save(region, account.toCredential())
                    val label = realmLabel(region)
                    breakdown[label] = 1
                    total = 1
                }
            }

            if (total == 0) {
                ImportResult(success = false, message = "未找到可导入的账号")
            } else {
                ImportResult(
                    success = true,
                    imported = total,
                    breakdown = breakdown,
                    message = "已导入 $total 个账号",
                )
            }
        }.getOrElse {
            ImportResult(success = false, message = "解析失败：${it.message ?: "格式不正确"}")
        }
    }

    private fun importArray(
        array: JSONArray?,
        forcedRegion: Wire.Region?,
        store: CredentialStore,
        breakdown: MutableMap<String, Int>,
    ): Int {
        if (array == null) return 0
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val account = readAccount(obj, forcedRegion) ?: continue
            store.save(account.region, account.toCredential())
            val label = realmLabel(account.region)
            breakdown[label] = (breakdown[label] ?: 0) + 1
            count++
        }
        return count
    }

    /**
     * 把一个 JSON 对象读成账号。
     *
     * [forcedRegion] 来自备份包的分组字段；单账号导入时为空，
     * 此时按 `domain` 推断其所属区域。
     */
    private fun readAccount(obj: JSONObject, forcedRegion: Wire.Region?): SavedAccount? {
        val token = obj.optString("accessToken")
        if (token.isEmpty()) return null

        val domain = obj.optString("domain")
        val region = forcedRegion
            ?: if (domain.isBlank()) Wire.Region.CN else Wire.regionOf(domain)

        val uid = obj.optString("uid")
        val nickname = obj.optString("nickname")
        val refresh = obj.optString("refreshToken")
        val enterpriseId = obj.optString("enterpriseId").ifEmpty { null }
        val source = obj.optString("source", "import")
        val storedExpiry = obj.optLong("expiresAt", 0L)
        val expiresAt = if (storedExpiry > 0L) storedExpiry else expiryFromJwt(token)

        return SavedAccount(
            id = if (uid.isNotBlank()) "$region:${uid.take(8)}" else "$region:$domain:${token.hashCode()}",
            region = region,
            nickname = nickname,
            uid = uid,
            domain = domain.ifEmpty { defaultDomain(region) },
            accessToken = token,
            refreshToken = refresh,
            expiresAt = expiresAt,
            enterpriseId = enterpriseId,
            source = source,
        )
    }

    private fun defaultDomain(region: Wire.Region): String =
        if (region == Wire.Region.GLOBAL) "www.workbuddy.ai" else Wire.CN_CHAT_BASE

    private fun realmLabel(region: Wire.Region): String =
        if (region == Wire.Region.GLOBAL) "国际版" else "国内版"

    /** 备份里可能没有 expiresAt，此时从 JWT 的 exp 声明还原。 */
    private fun expiryFromJwt(token: String): Long {
        val parts = token.split(".")
        if (parts.size < 2) return 0L
        return runCatching {
            val bytes = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
            JSONObject(String(bytes, StandardCharsets.UTF_8)).optLong("exp", 0L)
        }.getOrDefault(0L)
    }
}
