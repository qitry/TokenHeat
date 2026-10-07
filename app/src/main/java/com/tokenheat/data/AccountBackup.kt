package com.tokenheat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.tokenheat.proto.Credential
import com.tokenheat.proto.CredentialStore
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.Wire
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Handles exporting and importing full account rosters in compressed `.json.gz` bundle format.
 */
object AccountBackup {

    const val MIME_TYPE = "application/gzip"
    const val FILE_SUFFIX = ".json.gz"

    data class ImportResult(
        val success: Boolean,
        val totalAccounts: Int,
        val breakdown: Map<String, Int> = emptyMap(),
        val message: String = "",
    )

    /**
     * Serializes all stored credentials across every provider into a compressed gzip payload.
     */
    fun exportToGzipBytes(store: CredentialStore): ByteArray {
        val root = JSONObject().apply {
            put("version", 1)
            put("appName", "TokenHeat")
            put("exportedAt", System.currentTimeMillis())

            // WorkBuddy builds
            put("workbuddy_cn", serializeAccounts(store.accounts(Wire.Region.CN)))
            put("workbuddy_global", serializeAccounts(store.accounts(Wire.Region.GLOBAL)))

            // Dedicated providers
            put("zcode", serializeAccounts(store.zcodeAccounts()))
            put("zen", serializeAccounts(store.zenAccounts()))

            // Generic slots
            put("qoder_cn", serializeAccounts(store.slotAccounts(Provider.QODER_CN)))
            put("qoder_global", serializeAccounts(store.slotAccounts(Provider.QODER_GLOBAL)))
            put("antigravity", serializeAccounts(store.slotAccounts(Provider.ANTIGRAVITY)))
        }

        val jsonBytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(jsonBytes)
        }
        return baos.toByteArray()
    }

    /**
     * Writes the compressed account bundle to the public Downloads folder or cache,
     * and generates a system share Intent.
     */
    fun exportToFileAndGetIntent(context: Context, store: CredentialStore): Pair<File, Intent> {
        val bytes = exportToGzipBytes(store)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "tokenheat_accounts_$timestamp$FILE_SUFFIX"

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        targetDir.mkdirs()
        val file = File(targetDir, fileName)
        file.writeBytes(bytes)

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TokenHeat 账号备份 ($timestamp)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Pair(file, shareIntent)
    }

    /**
     * Reads a `.json.gz` input stream, decompresses and merges valid accounts into the CredentialStore.
     */
    fun importFromGzipStream(inputStream: InputStream, store: CredentialStore): ImportResult {
        return runCatching {
            val decompressedText = GZIPInputStream(inputStream).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(decompressedText)
            var count = 0
            val breakdown = mutableMapOf<String, Int>()

            // 1. WorkBuddy CN
            val wbCn = deserializeAccounts(root.optJSONArray("workbuddy_cn"), Wire.Region.CN, Provider.WORKBUDDY)
            wbCn.forEach { acc ->
                store.save(Wire.Region.CN, acc.toCredential())
                count++
            }
            if (wbCn.isNotEmpty()) breakdown["WorkBuddy 国内版"] = wbCn.size

            // 2. WorkBuddy Global
            val wbGlobal = deserializeAccounts(root.optJSONArray("workbuddy_global"), Wire.Region.GLOBAL, Provider.WORKBUDDY)
            wbGlobal.forEach { acc ->
                store.save(Wire.Region.GLOBAL, acc.toCredential())
                count++
            }
            if (wbGlobal.isNotEmpty()) breakdown["WorkBuddy 国际版"] = wbGlobal.size

            // 3. ZCode
            val zcode = deserializeAccounts(root.optJSONArray("zcode"), Wire.Region.CN, Provider.ZCODE)
            zcode.forEach { acc ->
                store.saveZcode(acc.toCredential())
                count++
            }
            if (zcode.isNotEmpty()) breakdown["ZCode"] = zcode.size

            // 4. Zen
            val zen = deserializeAccounts(root.optJSONArray("zen"), Wire.Region.CN, Provider.ZEN)
            zen.forEach { acc ->
                store.saveZen(acc.toCredential())
                count++
            }
            if (zen.isNotEmpty()) breakdown["Zen"] = zen.size

            // 5. Qoder CN
            val qoderCn = deserializeAccounts(root.optJSONArray("qoder_cn"), Wire.Region.CN, Provider.QODER_CN)
            qoderCn.forEach { acc ->
                store.saveSlot(Provider.QODER_CN, acc.toCredential())
                count++
            }
            if (qoderCn.isNotEmpty()) breakdown["Qoder 国内版"] = qoderCn.size

            // 6. Qoder Global
            val qoderGlobal = deserializeAccounts(root.optJSONArray("qoder_global"), Wire.Region.GLOBAL, Provider.QODER_GLOBAL)
            qoderGlobal.forEach { acc ->
                store.saveSlot(Provider.QODER_GLOBAL, acc.toCredential())
                count++
            }
            if (qoderGlobal.isNotEmpty()) breakdown["Qoder 国际版"] = qoderGlobal.size

            // 7. Antigravity CLI
            val ag = deserializeAccounts(root.optJSONArray("antigravity"), Wire.Region.CN, Provider.ANTIGRAVITY)
            ag.forEach { acc ->
                store.saveSlot(Provider.ANTIGRAVITY, acc.toCredential())
                count++
            }
            if (ag.isNotEmpty()) breakdown["Antigravity CLI"] = ag.size

            ImportResult(
                success = true,
                totalAccounts = count,
                breakdown = breakdown,
                message = "成功导入 $count 个账号",
            )
        }.getOrElse { e ->
            ImportResult(
                success = false,
                totalAccounts = 0,
                message = "解析账号包失败：${e.message?.take(80) ?: "格式不兼容"}",
            )
        }
    }

    private fun serializeAccounts(accounts: List<SavedAccount>): JSONArray {
        val array = JSONArray()
        accounts.forEach { acc ->
            array.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("nickname", acc.nickname)
                    put("uid", acc.uid)
                    put("domain", acc.domain)
                    put("accessToken", acc.accessToken)
                    put("refreshToken", acc.refreshToken)
                    put("apiKey", acc.apiKey)
                    put("projectId", acc.projectId)
                    put("expiresAt", acc.expiresAt)
                    put("enterpriseId", acc.enterpriseId ?: "")
                    put("disabled", acc.disabled)
                    put("source", acc.source)
                },
            )
        }
        return array
    }

    private fun deserializeAccounts(array: JSONArray?, region: Wire.Region, provider: Provider): List<SavedAccount> {
        array ?: return emptyList()
        val list = mutableListOf<SavedAccount>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val token = obj.optString("accessToken")
            val apiKey = obj.optString("apiKey")
            if (token.isEmpty() && apiKey.isEmpty()) continue

            list.add(
                SavedAccount(
                    id = obj.optString("id"),
                    provider = provider,
                    region = region,
                    nickname = obj.optString("nickname"),
                    uid = obj.optString("uid"),
                    domain = obj.optString("domain"),
                    accessToken = token,
                    refreshToken = obj.optString("refreshToken"),
                    apiKey = apiKey,
                    projectId = obj.optString("projectId"),
                    disabled = obj.optBoolean("disabled", false),
                    expiresAt = obj.optLong("expiresAt", 0L),
                    enterpriseId = obj.optString("enterpriseId").ifEmpty { null },
                    source = obj.optString("source", "import"),
                ),
            )
        }
        return list
    }
}
