package app.anylyralabs.lyraproxy.bridge

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 签到状态记录。
 *
 * 上游的签到接口本身不返回「今天是否已经签到」，重复调用只会返回一个
 * 不痛不痒的提示。与其让用户每次点一下才知道，不如在本地记住最近一次
 * 成功的签到日期：同一天内再次打开应用，直接显示「已签到」。
 *
 * 只记日期而不是完整历史：签到是「每天一次」的语义，跨天自动失效，
 * 不需要清理，也不会无限增长。
 */
class CheckinStore(context: Context) {

    private val prefs = context.getSharedPreferences("lyra-checkin", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dayFormat.format(Date())

    /** 该账号今天是否已签到成功。以账号 id 为键，切号不会串。 */
    fun isCheckedToday(accountId: String): Boolean {
        if (accountId.isBlank()) return false
        return prefs.getString(accountId, null) == today()
    }

    /** 记录一次成功签到。失败不写，避免把失败误标成已签到。 */
    fun markChecked(accountId: String) {
        if (accountId.isBlank()) return
        prefs.edit().putString(accountId, today()).apply()
    }

    /** 清除某账号的记录，用于「退出登录」或删除账号。 */
    fun clear(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }

    /** 清除全部，用于整体登出。 */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
