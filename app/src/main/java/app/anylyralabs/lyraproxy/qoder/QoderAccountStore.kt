package app.anylyralabs.lyraproxy.qoder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Qoder 账号与机器标识的本地存储。
 *
 * 支持多账号：文件保存账号数组与当前选中的 id。旧版本写入的是单个对象，
 * 读取时会自动迁移，不会让已登录用户掉线。
 *
 * 机器标识单独持久化：Qoder 的请求头里需要它，而且同一个账号换机器标识
 * 可能被判定为异常登录，所以首次生成后要一直复用。
 */
class QoderAccountStore(context: Context) {

    private val file = File(context.filesDir, "lyra-qoder.json")
    private val modelFile = File(context.filesDir, "lyra-qoder-models.json")
    private val prefs = context.getSharedPreferences("lyra-qoder", Context.MODE_PRIVATE)

    /** 稳定的机器标识；首次调用时生成并保存。 */
    fun machineId(): String {
        prefs.getString(KEY_MACHINE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MACHINE_ID, generated).apply()
        return generated
    }

    // --------------------------------------------------------------------- //
    // 账号
    // --------------------------------------------------------------------- //

    fun accounts(): List<QoderAccount> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val root = JSONObject(text)
            val array = root.optJSONArray("accounts")
            if (array != null) {
                (0 until array.length()).mapNotNull { readAccount(array.optJSONObject(it)) }
            } else {
                // 旧格式：整个文件就是一个账号对象。
                listOfNotNull(readAccount(root))
            }
        }.getOrDefault(emptyList())
    }

    /** 当前选中的账号；没有选中记录时退回第一个。 */
    fun active(): QoderAccount? {
        val list = accounts()
        if (list.isEmpty()) return null
        val activeId = activeId()
        return list.firstOrNull { it.id == activeId } ?: list.first()
    }

    /** 新增或更新账号，并把它设为当前账号。 */
    fun save(account: QoderAccount) {
        val list = accounts().toMutableList()
        val index = list.indexOfFirst { it.id == account.id }
        if (index >= 0) list[index] = account else list.add(account)
        write(list, account.id)
    }

    fun select(id: String) {
        val list = accounts()
        if (list.none { it.id == id }) return
        write(list, id)
    }

    fun delete(id: String) {
        val remaining = accounts().filterNot { it.id == id }
        if (remaining.isEmpty()) {
            file.delete()
            return
        }
        write(remaining, remaining.first().id)
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun activeId(): String? = runCatching {
        if (!file.exists()) null else JSONObject(file.readText()).optString("active").ifEmpty { null }
    }.getOrNull()

    private fun readAccount(obj: JSONObject?): QoderAccount? {
        obj ?: return null
        val token = obj.optString("accessToken")
        if (token.isEmpty()) return null
        return QoderAccount(
            id = obj.optString("id").ifEmpty { "qoder" },
            name = obj.optString("name"),
            email = obj.optString("email"),
            accessToken = token,
            refreshToken = obj.optString("refreshToken"),
            expiresAt = obj.optLong("expiresAt", 0L),
            machineId = obj.optString("machineId").ifEmpty { machineId() },
            source = obj.optString("source", "oauth"),
        )
    }

    private fun write(accounts: List<QoderAccount>, activeId: String) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("id", account.id)
                    put("name", account.name)
                    put("email", account.email)
                    put("accessToken", account.accessToken)
                    put("refreshToken", account.refreshToken)
                    put("expiresAt", account.expiresAt)
                    put("machineId", account.machineId)
                    put("source", account.source)
                },
            )
        }
        val root = JSONObject().apply {
            put("version", 2)
            put("active", activeId)
            put("accounts", array)
        }
        runCatching { file.writeText(root.toString(2)) }
    }

    // --------------------------------------------------------------------- //
    // 模型缓存
    // --------------------------------------------------------------------- //

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
                    priceFactor = obj.optDouble("priceFactor", 0.0),
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
                    put("priceFactor", model.priceFactor)
                    put("raw", model.raw)
                },
            )
        }
        runCatching { modelFile.writeText(array.toString()) }
    }

    private companion object {
        const val KEY_MACHINE_ID = "machine_id"
    }
}
