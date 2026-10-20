package app.anylyralabs.lyraproxy.qoder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Qoder 账号与机器标识的本地存储。
 *
 * 机器标识单独持久化：Qoder 的请求头里需要它，而且同一个账号换机器标识
 * 可能被判定为异常登录，所以首次生成后要一直复用。
 */
class QoderAccountStore(context: Context) {

    private val file = File(context.filesDir, "lyra-qoder.json")
    private val prefs = context.getSharedPreferences("lyra-qoder", Context.MODE_PRIVATE)

    /** 稳定的机器标识；首次调用时生成并保存。 */
    fun machineId(): String {
        prefs.getString(KEY_MACHINE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MACHINE_ID, generated).apply()
        return generated
    }

    fun load(): QoderAccount? {
        if (!file.exists()) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            val token = obj.optString("accessToken")
            if (token.isEmpty()) return null
            QoderAccount(
                id = obj.optString("id").ifEmpty { "qoder" },
                name = obj.optString("name"),
                email = obj.optString("email"),
                accessToken = token,
                refreshToken = obj.optString("refreshToken"),
                expiresAt = obj.optLong("expiresAt", 0L),
                machineId = obj.optString("machineId").ifEmpty { machineId() },
                source = obj.optString("source", "oauth"),
            )
        }.getOrNull()
    }

    fun save(account: QoderAccount) {
        val obj = JSONObject().apply {
            put("id", account.id)
            put("name", account.name)
            put("email", account.email)
            put("accessToken", account.accessToken)
            put("refreshToken", account.refreshToken)
            put("expiresAt", account.expiresAt)
            put("machineId", account.machineId)
            put("source", account.source)
        }
        runCatching { file.writeText(obj.toString(2)) }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    /** 缓存的模型目录，避免每次启动都请求一次。 */
    fun loadModels(): List<QoderModel> {
        if (!modelFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(modelFile.readText())
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val key = obj.optString("key")
                val display = obj.optString("displayName")
                if (key.isEmpty() && display.isEmpty()) return@mapNotNull null
                QoderModel(
                    key = key,
                    displayName = display.ifEmpty { key },
                    supportsImages = obj.optBoolean("supportsImages", false),
                    supportsReasoning = obj.optBoolean("supportsReasoning", false),
                    contextWindow = obj.optInt("contextWindow", 0),
                    raw = obj.optString("raw"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveModels(models: List<QoderModel>) {
        val array = JSONArray()
        models.forEach { model ->
            array.put(
                JSONObject().apply {
                    put("key", model.key)
                    put("displayName", model.displayName)
                    put("supportsImages", model.supportsImages)
                    put("supportsReasoning", model.supportsReasoning)
                    put("contextWindow", model.contextWindow)
                    put("raw", model.raw)
                },
            )
        }
        runCatching { modelFile.writeText(array.toString()) }
    }

    private val modelFile = File(file.parentFile, "lyra-qoder-models.json")

    private companion object {
        const val KEY_MACHINE_ID = "machine_id"
    }
}
