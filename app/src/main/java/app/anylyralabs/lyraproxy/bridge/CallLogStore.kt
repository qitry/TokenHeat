package app.anylyralabs.lyraproxy.bridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One completed chat call, as recorded for the history view.
 *
 * Token counts and cost come from the upstream's own usage block, so the credit
 * figure reflects what the account was actually charged rather than an estimate.
 */
data class CallRecord(
    val timestamp: Long,
    val model: String,
    val outcome: Outcome,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val credits: Double = 0.0,
    val detail: String = "",
) {
    enum class Outcome { OK, FAILED }

    val totalTokens: Int get() = promptTokens + completionTokens

    val timeText: String
        get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Append-only ledger of every bridge call, kept in an NDJSON file.
 *
 * 与旧实现（整份 JSON 重写 + 500 条上限）的关键差异：
 * - 写入是**追加**一行，代价 O(1)，因此可以长期保存全部流水；
 * - 读取时流式解析，逐行转成 [CallRecord]；
 * - 上限只作为磁盘保护（[MAX_ENTRIES]），远高于日常使用量，触顶时才做一次压缩，
 *   且压缩保留最新记录，不会出现「统计被截断」的错觉。
 */
class CallLogStore(context: Context) {

    private val file = File(context.filesDir, "lyra-call-log.ndjson")

    /** 旧版本使用的文件名，升级后自动迁移一次。 */
    private val legacyFile = File(context.filesDir, "wb-call-log.json")
    private val lock = Any()

    init {
        migrateLegacy()
    }

    /**
     * 把旧版「整份 JSON 数组」格式迁移成 NDJSON。
     * 旧文件在达到 500 条上限前的内容会全部保留，之后就接续追加。
     */
    private fun migrateLegacy() {
        if (file.exists() || !legacyFile.exists()) return
        synchronized(lock) {
            runCatching {
                val text = legacyFile.readText()
                val array = JSONArray(text)
                val builder = StringBuilder()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    builder.append(item.toString()).append('\n')
                }
                if (builder.isNotEmpty()) file.writeText(builder.toString())
                legacyFile.delete()
            }.onFailure {
                // 迁移失败时保留旧文件，不要删掉用户的记录。
                android.util.Log.w("LyraProxy", "call log migration failed", it)
            }
        }
    }

    /**
     * 读取全部记录，按写入顺序（旧 → 新）返回。
     *
     * 逐行容错：单行损坏只跳过该行，不会让整份历史作废——旧实现一旦
     * JSON 损坏就返回空列表，等于把用户的累计统计清零。
     */
    fun load(): List<CallRecord> {
        if (!file.exists()) return emptyList()
        val result = ArrayList<CallRecord>()
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    parseLine(line)?.let(result::add)
                }
            }
        }
        return result
    }

    private fun parseLine(line: String): CallRecord? = runCatching {
        val item = JSONObject(line)
        CallRecord(
            timestamp = item.optLong("ts"),
            model = item.optString("model"),
            outcome = runCatching {
                CallRecord.Outcome.valueOf(item.optString("outcome"))
            }.getOrDefault(CallRecord.Outcome.FAILED),
            promptTokens = item.optInt("prompt"),
            completionTokens = item.optInt("completion"),
            credits = item.optDouble("credits", 0.0),
            detail = item.optString("detail"),
        )
    }.getOrNull()

    fun append(record: CallRecord) {
        synchronized(lock) {
            runCatching {
                val line = JSONObject().apply {
                    put("ts", record.timestamp)
                    put("model", record.model)
                    put("outcome", record.outcome.name)
                    put("prompt", record.promptTokens)
                    put("completion", record.completionTokens)
                    put("credits", record.credits)
                    put("detail", record.detail)
                }.toString()
                file.appendText(line + "\n")
                // 只在明显超过保护上限时才整理一次，避免每次写入都全量读盘。
                if (file.length() > COMPACT_THRESHOLD_BYTES) compact()
            }
        }
    }

    /** 保留最新的 [MAX_ENTRIES] 条，其余丢弃；写临时文件后原子替换。 */
    private fun compact() {
        val all = load()
        if (all.size <= MAX_ENTRIES) return
        val kept = all.subList(all.size - MAX_ENTRIES, all.size)
        val temp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            temp.bufferedWriter().use { writer ->
                kept.forEach { entry ->
                    writer.write(
                        JSONObject().apply {
                            put("ts", entry.timestamp)
                            put("model", entry.model)
                            put("outcome", entry.outcome.name)
                            put("prompt", entry.promptTokens)
                            put("completion", entry.completionTokens)
                            put("credits", entry.credits)
                            put("detail", entry.detail)
                        }.toString(),
                    )
                    writer.newLine()
                }
            }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }
    }

    fun clear() {
        synchronized(lock) { runCatching { file.delete() } }
    }

    /**
     * 文件指纹：长度 + 修改时间。
     *
     * 供界面轮询判断「有没有新记录」，避免每次轮询都全量解析整个日志。
     * 追加写入必然改变长度，因此长度是最可靠的判据；时间戳作为补充。
     */
    fun stamp(): String {
        if (!file.exists()) return "0:0"
        return "${file.length()}:${file.lastModified()}"
    }

    private companion object {
        /**
         * 磁盘保护上限。这不是「只留 500 条」的功能性截断：按每行约 200 字节估算，
         * 20 万条约 40 MB，普通使用数年都到不了；触顶时才保留最新的记录。
         */
        const val MAX_ENTRIES = 200_000

        /** 超过该文件体积才尝试压缩，避免每写一条就全量读一遍。 */
        const val COMPACT_THRESHOLD_BYTES = 64L * 1024 * 1024
    }
}

/** Totals derived from the recorded calls, for the summary row. */
data class UsageSummary(
    val calls: Int,
    val failures: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val credits: Double,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    companion object {
        fun of(records: List<CallRecord>): UsageSummary = UsageSummary(
            calls = records.count { it.outcome == CallRecord.Outcome.OK },
            failures = records.count { it.outcome == CallRecord.Outcome.FAILED },
            promptTokens = records.sumOf { it.promptTokens },
            completionTokens = records.sumOf { it.completionTokens },
            credits = records.sumOf { it.credits },
        )
    }
}
