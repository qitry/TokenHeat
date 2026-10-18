package app.anylyralabs.lyraproxy.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * 一个自定义 API Key。
 *
 * 网关原本只有一条共享密钥，任何拿到它的人都能调用全部模型、消耗全部额度。
 * 这里把密钥升级为「凭据 + 约束」：
 * - [allowedModels] 为空表示不限制，否则只放行列出的模型；
 * - [budget] 为 0 表示不限额，否则累计积分达到该值后拒绝新的请求。
 */
data class ApiKey(
    val id: String,
    val label: String,
    val key: String,
    val allowedModels: List<String> = emptyList(),
    /** 积分预算；0 表示不限制。 */
    val budget: Double = 0.0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** 已消耗积分，由网关在每次成功调用后累加。 */
    val usedCredits: Double = 0.0,
    val requestCount: Int = 0,
) {
    val isUnlimitedBudget: Boolean get() = budget <= 0.0

    val budgetRemaining: Double get() = if (isUnlimitedBudget) Double.MAX_VALUE else (budget - usedCredits).coerceAtLeast(0.0)

    fun modelAllowed(model: String): Boolean =
        allowedModels.isEmpty() || allowedModels.any { it.equals(model, ignoreCase = true) }

    fun budgetAllows(): Boolean = isUnlimitedBudget || usedCredits < budget
}

/**
 * 自定义 API Key 的持久化。
 *
 * 存在应用私有目录，与凭证分开：密钥本身是给外部客户端用的，
 * 与上游登录态是两类东西，混在一起会让备份与清除的语义变模糊。
 */
class ApiKeyStore(context: Context) {

    private val file = File(context.filesDir, "lyra-api-keys.json")
    private val lock = Any()

    fun load(): List<ApiKey> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val key = item.optString("key")
                if (key.isEmpty()) return@mapNotNull null
                ApiKey(
                    id = item.optString("id").ifEmpty { key.take(8) },
                    label = item.optString("label"),
                    key = key,
                    allowedModels = item.optJSONArray("allowedModels").toStringList(),
                    budget = item.optDouble("budget", 0.0),
                    enabled = item.optBoolean("enabled", true),
                    createdAt = item.optLong("createdAt", 0L),
                    usedCredits = item.optDouble("usedCredits", 0.0),
                    requestCount = item.optInt("requestCount", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun save(keys: List<ApiKey>) {
        val array = JSONArray()
        keys.forEach { key ->
            array.put(
                JSONObject().apply {
                    put("id", key.id)
                    put("label", key.label)
                    put("key", key.key)
                    put("allowedModels", JSONArray(key.allowedModels))
                    put("budget", key.budget)
                    put("enabled", key.enabled)
                    put("createdAt", key.createdAt)
                    put("usedCredits", key.usedCredits)
                    put("requestCount", key.requestCount)
                },
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    /** 新增一个密钥；[key] 为空时自动生成。 */
    fun create(
        label: String,
        key: String = generateKey(),
        allowedModels: List<String> = emptyList(),
        budget: Double = 0.0,
    ): ApiKey {
        synchronized(lock) {
            val created = ApiKey(
                id = System.currentTimeMillis().toString(36) + key.takeLast(4),
                label = label.ifBlank { "未命名密钥" },
                key = key,
                allowedModels = allowedModels.distinct(),
                budget = budget,
            )
            save(load() + created)
            return created
        }
    }

    fun update(updated: ApiKey) {
        synchronized(lock) {
            save(load().map { if (it.id == updated.id) updated else it })
        }
    }

    fun delete(id: String) {
        synchronized(lock) { save(load().filterNot { it.id == id }) }
    }

    fun clearUsage(id: String) {
        synchronized(lock) {
            save(load().map { if (it.id == id) it.copy(usedCredits = 0.0, requestCount = 0) else it })
        }
    }

    /**
     * 记一次调用产生的积分与请求数。
     * 按 key 的 id 累加，避免用户改了密钥字符串后统计丢失。
     */
    fun recordUsage(id: String, credits: Double) {
        synchronized(lock) {
            save(
                load().map {
                    if (it.id == id) {
                        it.copy(
                            usedCredits = it.usedCredits + credits.coerceAtLeast(0.0),
                            requestCount = it.requestCount + 1,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    companion object {
        private const val KEY_PREFIX = "lyra-"

        /** 生成 `lyra-` 前缀的随机密钥，便于在客户端里一眼认出。 */
        fun generateKey(): String {
            val random = SecureRandom()
            val bytes = ByteArray(24)
            random.nextBytes(bytes)
            val body = bytes.joinToString("") { "%02x".format(it) }
            return KEY_PREFIX + body
        }
    }
}
