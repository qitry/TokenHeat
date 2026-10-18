package app.anylyralabs.lyraproxy.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 后台保活工具。
 *
 * Android 没有「把应用加入白名单」的公开 API，所以这里只做两件能做的事：
 * 查询电池优化状态，以及把用户引导到真正能改设置的系统页面。
 * 各家 ROM 的自启/后台限制页面无法深链，只能按厂商给出文字指引。
 *
 * 移植自 termux-app 的 keepalive/KeepAliveUtils，去掉了 Termux 专有的会话统计。
 */
object KeepAliveUtils {

    /** 是否已被豁免电池优化。 */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 拉起系统的「忽略电池优化」授权页；部分 ROM 禁用了直接请求，回退到设置列表。 */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (isBatteryOptimizationIgnored(activity)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        runCatching { activity.startActivity(direct) }.onFailure {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /** 打开应用详情页，自启动与后台运行开关通常在这里。 */
    fun openAppDetailsSettings(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        }
    }

    /** 打开悬浮窗权限页；缺失该页面时回退到应用详情。 */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val overlay = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(overlay) }.onFailure {
            openAppDetailsSettings(activity)
        }
    }

    /** 是否已授予「显示在其他应用上层」。 */
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
        else Settings.canDrawOverlays(context)

    /**
     * 按厂商给出的后台保活提示。
     * 这些页面没有公开的 deep link，只能由用户按文字自行前往。
     */
    fun romHint(): String {
        val maker = (Build.MANUFACTURER ?: "").lowercase()
        return when {
            maker.contains("oppo") || maker.contains("oneplus") || maker.contains("realme") ->
                "ColorOS / realme UI：设置 → 电池 → 允许后台运行，并在最近任务中下拉锁定本应用。"
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") ->
                "HyperOS / MIUI：应用设置 → 自启动，并将省电策略设为「无限制」。"
            maker.contains("huawei") || maker.contains("honor") ->
                "EMUI / HarmonyOS：应用启动管理 → 手动管理，允许自启动、关联启动与后台活动。"
            maker.contains("vivo") || maker.contains("iqoo") ->
                "OriginOS：后台高耗电 → 允许后台高耗电；i 管家 → 自启动中允许本应用。"
            maker.contains("samsung") ->
                "One UI：电池 → 后台使用限制，将本应用加入「永不休眠的应用」。"
            else ->
                "在系统设置中允许本应用自启动与后台运行，并在最近任务中锁定它。"
        }
    }
}
